package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskResultMessage;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskLogMapper;
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
@RocketMQMessageListener(topic = "TASK_RESULT", consumerGroup = "WORKER_EXECUTOR_GROUP")
@Slf4j
public class TaskResultConsumer implements RocketMQListener<TaskResultMessage> {

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;
    
    /** 任务日志Mapper */
    private final TaskLogMapper taskLogMapper;

    @Autowired
    public TaskResultConsumer(TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper) {
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
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
        
        // 创建或更新任务运行实例
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
                .build();
        log.debug("TaskRun对象构建完成，ID: {}", taskRun.getId());

        // 保存或更新任务执行结果（避免重复键异常）
        log.info("保存任务执行结果，TaskRunID: {}, Status: {}", taskRun.getId(), taskRun.getStatus());
        try {
            taskRunMapper.insertOrUpdate(taskRun);
            log.info("任务执行结果保存成功，TaskRunID: {}", taskRun.getId());
        } catch (Exception e) {
            log.error("任务执行结果保存失败，TaskRunID: {}, Error: {}", taskRun.getId(), e.getMessage(), e);
            return;
        }
        
        // 保存日志路径到task_log表
        if (resultMessage.getLogPath() != null && !resultMessage.getLogPath().isEmpty()) {
            log.info("开始保存任务日志，TaskRunID: {}, LogPath: {}", 
                    resultMessage.getTaskRunId(), resultMessage.getLogPath());
            TaskLog taskLog = TaskLog.builder()
                    .runId(resultMessage.getTaskRunId())
                    .stdoutPath(resultMessage.getLogPath())
                    .stderrPath(resultMessage.getLogPath()) // 目前只支持单个日志路径，stdout和stderr使用相同路径
                    .createTime(LocalDateTime.now())
                    .build();
            
            // 先查询是否已存在日志记录
            try {
                TaskLog existingLog = taskLogMapper.selectById(resultMessage.getTaskRunId());
                if (existingLog != null) {
                    // 更新现有记录
                    log.debug("日志记录已存在，更新现有记录，TaskRunID: {}", resultMessage.getTaskRunId());
                    taskLogMapper.updateById(taskLog);
                    log.info("日志记录更新成功，TaskRunID: {}", resultMessage.getTaskRunId());
                } else {
                    // 插入新记录
                    log.debug("日志记录不存在，插入新记录，TaskRunID: {}", resultMessage.getTaskRunId());
                    taskLogMapper.insert(taskLog);
                    log.info("日志记录插入成功，TaskRunID: {}", resultMessage.getTaskRunId());
                }
            } catch (Exception e) {
                log.error("日志记录保存失败，TaskRunID: {}, Error: {}", 
                        resultMessage.getTaskRunId(), e.getMessage(), e);
            }
        } else {
            log.debug("未包含日志路径，跳过日志记录保存，TaskRunID: {}", resultMessage.getTaskRunId());
        }
        
        log.info("任务执行结果处理完成，TaskRunID: {}", resultMessage.getTaskRunId());
    }
}