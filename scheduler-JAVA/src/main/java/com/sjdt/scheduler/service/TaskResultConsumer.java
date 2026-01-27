package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskResultMessage;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * RocketMQ消费者服务
 * 负责接收Worker返回的任务执行结果
 */
@Service
@RocketMQMessageListener(topic = "TASK_RESULT", consumerGroup = "SCHEDULER_RESULT_GROUP")
@Slf4j
public class TaskResultConsumer implements RocketMQListener<TaskResultMessage> {

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;
    
    /** 任务日志Mapper */
    private final TaskLogMapper taskLogMapper;

    /** 任务服务 */
    private final ITaskService taskService;

    /** 任务Mapper */
    private final TaskMapper taskMapper;

    @Autowired
    public TaskResultConsumer(TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper, ITaskService taskService, TaskMapper taskMapper) {
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
    }

    /**
     * 接收任务执行结果
     *
     * @param resultMessage 任务执行结果消息
     */
    @Override
    public void onMessage(TaskResultMessage resultMessage) {
        log.info("收到任务执行结果，TaskRunID: {}, TaskID: {}, Status: {}", 
                resultMessage.getTaskRunId(), resultMessage.getTaskId(), resultMessage.getStatus());
        
        // 1. 获取原始TaskRun记录（获取retryNum等信息）
        TaskRun originalRun = taskRunMapper.selectById(resultMessage.getTaskRunId());
        int currentRetryNum = 0;
        if (originalRun != null && originalRun.getRetryNum() != null) {
            currentRetryNum = originalRun.getRetryNum();
        }

        // 2. 创建或更新任务运行实例
        log.debug("开始构建TaskRun对象，WorkerID: {}, ExitCode: {}", 
                resultMessage.getWorkerId(), resultMessage.getExitCode());
        TaskRun taskRun = TaskRun.builder()
                .id(resultMessage.getTaskRunId())
                .taskId(resultMessage.getTaskId())
                .workerId(resultMessage.getWorkerId())
                .status(TaskRunStatus.of(resultMessage.getStatus()))
                .exitCode(resultMessage.getExitCode())
                .startTime(resultMessage.getStartTime())
                .endTime(resultMessage.getEndTime())
                .durationMs(resultMessage.getDurationMs())
                .errorMessage(resultMessage.getErrorMessage())
                .retryNum(currentRetryNum) // 保持原有的重试次数
                .lastHeartbeatTime(LocalDateTime.now()) // 更新心跳时间
                .build();
        log.debug("TaskRun对象构建完成，ID: {}", taskRun.getId());

        // 3. 保存或更新任务执行结果（避免重复键异常）
        log.info("保存任务执行结果，TaskRunID: {}, Status: {}", taskRun.getId(), taskRun.getStatus());
        try {
            taskRunMapper.insertOrUpdate(taskRun);
            log.info("任务执行结果保存成功，TaskRunID: {}", taskRun.getId());
        } catch (Exception e) {
            log.error("任务执行结果保存失败，TaskRunID: {}, Error: {}", taskRun.getId(), e.getMessage(), e);
            return;
        }
        
        // 4. 保存日志路径到task_log表
        saveTaskLog(resultMessage);

        // 5. 检查是否需要重试
        checkAndTriggerRetry(taskRun, resultMessage.getStatus());

        log.info("任务执行结果处理完成，TaskRunID: {}", resultMessage.getTaskRunId());
    }

    /**
     * 检查并触发重试
     */
    private void checkAndTriggerRetry(TaskRun taskRun, String status) {
        // 只有失败或超时才考虑重试
        if (!"failed".equalsIgnoreCase(status) && !"timeout".equalsIgnoreCase(status)) {
            return;
        }

        try {
            Task task = taskMapper.selectById(taskRun.getTaskId());
            if (task == null) {
                log.warn("任务不存在，无法进行重试判断，TaskID: {}", taskRun.getTaskId());
                return;
            }

            Integer maxRetryCount = task.getRetryCount();
            if (maxRetryCount == null || maxRetryCount <= 0) {
                return;
            }

            Integer currentRetryNum = taskRun.getRetryNum();
            if (currentRetryNum == null) currentRetryNum = 0;

            if (currentRetryNum < maxRetryCount) {
                int nextRetryNum = currentRetryNum + 1;
                int intervalSeconds = task.getRetryIntervalSeconds() != null ? task.getRetryIntervalSeconds() : 60;
                
                log.info("任务执行失败/超时，准备重试。TaskID: {}, 当前重试: {}/{}, 间隔: {}秒", 
                        task.getId(), currentRetryNum, maxRetryCount, intervalSeconds);

                // 改用 RocketMQ 延时消息
                taskService.publishTaskWithRetryAndDelay(task.getId(), nextRetryNum, intervalSeconds);
            } else {
                log.info("任务执行失败/超时，已达到最大重试次数，不再重试。TaskID: {}, 当前重试: {}/{}", 
                        task.getId(), currentRetryNum, maxRetryCount);
            }
        } catch (Exception e) {
            log.error("重试检查逻辑异常", e);
        }
    }

    private void saveTaskLog(TaskResultMessage resultMessage) {
        if (resultMessage.getLogPath() != null && !resultMessage.getLogPath().isEmpty()) {
            log.info("开始保存任务日志，TaskRunID: {}, LogPath: {}", 
                    resultMessage.getTaskRunId(), resultMessage.getLogPath());
            TaskLog taskLog = TaskLog.builder()
                    .runId(resultMessage.getTaskRunId())
                    .stdoutPath(resultMessage.getLogPath())
                    .stderrPath(resultMessage.getLogPath())
                    .createTime(LocalDateTime.now())
                    .build();
            
            try {
                TaskLog existingLog = taskLogMapper.selectById(resultMessage.getTaskRunId());
                if (existingLog != null) {
                    taskLogMapper.updateById(taskLog);
                } else {
                    taskLogMapper.insert(taskLog);
                }
            } catch (Exception e) {
                log.error("日志记录保存失败，TaskRunID: {}, Error: {}", 
                        resultMessage.getTaskRunId(), e.getMessage(), e);
            }
        }
    }
}