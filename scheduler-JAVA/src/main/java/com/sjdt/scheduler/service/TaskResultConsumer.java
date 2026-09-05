package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.List;

/**
 * RocketMQ消费者服务
 * 负责接收Worker返回的任务执行结果
 *
 * 消费组通过配置 rocketmq.consumer.group 控制（对应环境变量 ROCKETMQ_CONSUMER_GROUP），
 * 便于多环境隔离（如本机独立测试环境使用独立消费组，避免与其它调度器实例竞争 TASK_RESULT）。
 */
@Service
@RocketMQMessageListener(topic = "TASK_RESULT",
        consumerGroup = "${rocketmq.consumer.group:SCHEDULER_RESULT_GROUP}")
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

    /** 分片结算服务 */
    private final ShardSettlementService shardSettlementService;

    @Autowired
    public TaskResultConsumer(TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper,
                              ITaskService taskService, TaskMapper taskMapper,
                              ShardSettlementService shardSettlementService) {
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskService = taskService;
        this.taskMapper = taskMapper;
        this.shardSettlementService = shardSettlementService;
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
        
        TaskRun originalRun = taskRunMapper.selectById(resultMessage.getTaskRunId());
        int currentRetryNum = 0;
        if (originalRun != null && originalRun.getRetryNum() != null) {
            currentRetryNum = originalRun.getRetryNum();
            log.info("原有任务实例开始时间为{}, 结束时间为{} id为{}", originalRun.getStartTime(), originalRun.getEndTime(), resultMessage.getTaskRunId());
        }


        log.debug("开始构建TaskRun对象，WorkerID: {}, ExitCode: {}", 
                resultMessage.getWorkerId(), resultMessage.getExitCode());
        TaskRun taskRun = TaskRun.builder()
                .id(resultMessage.getTaskRunId())
                .taskId(resultMessage.getTaskId())
                .workerId(resultMessage.getWorkerId())
                .status(TaskRunStatus.of(resultMessage.getStatus()))
                .exitCode(resultMessage.getExitCode())
                .endTime(LocalDateTime.now())
                .durationMs(resultMessage.getDurationMs())
                .errorMessage(resultMessage.getErrorMessage())
                .retryNum(currentRetryNum) // 保持原有的重试次数
                .lastHeartbeatTime(LocalDateTime.now()) // 更新心跳时间
                .build();
        log.debug("TaskRun对象构建完成，ID: {}", taskRun.getId());
        log.info("TaskRun对象构建完成, 开始时间为{}, 结束时间为{} ", taskRun.getStartTime(), taskRun.getEndTime());

        // 保存或更新任务执行结果（避免重复键异常）
        log.info("保存任务执行结果，TaskRunID: {}, Status: {}", taskRun.getId(), taskRun.getStatus());
        try {
            taskRunMapper.insertOrUpdate(taskRun);
            log.info("任务执行结果保存成功，TaskRunID: {}", taskRun.getId());
        } catch (Exception e) {
            log.error("任务执行结果保存失败，TaskRunID: {}, Error: {}", taskRun.getId(), e.getMessage(), e);
            return;
        }
        
        saveTaskLog(resultMessage);

        // 终态 -> 触发分片结算检查（非分片任务为 N=1 特例，同路径）
        shardSettlementService.onRunTerminal(resultMessage.getTaskRunId());

        // 检查是否需要重试
        checkAndTriggerRetry(taskRun, resultMessage.getStatus(), originalRun);

        log.info("任务执行结果处理完成，TaskRunID: {}", resultMessage.getTaskRunId());
    }

    /**
     * 检查并触发重试
     * 分片任务携带原片身份（shardIndex/shardTotal/shardBatchId）：单片重试不换片
     *
     * @param taskRun 本次结果构建的run记录（含retryNum）
     * @param status 结果状态
     * @param originalRun DB中的原run记录（携带分片身份）
     */
    private void checkAndTriggerRetry(TaskRun taskRun, String status, TaskRun originalRun) {
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

                // 改用 RocketMQ 延时消息；携带原片分片身份 -> 单片重试不换片（hash 固定，不重不漏）
                Integer shardIndex = originalRun != null ? originalRun.getShardIndex() : null;
                Integer shardTotal = originalRun != null ? originalRun.getShardTotal() : null;
                String shardBatchId = originalRun != null ? originalRun.getShardBatchId() : null;
                taskService.publishTaskWithRetryAndDelay(task.getId(), nextRetryNum, intervalSeconds,
                        shardIndex, shardTotal, shardBatchId);
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
            
            try {
                // 根据 runId 查询现有的日志记录，使用 selectList 并取第一个以防历史数据中存在重复
                List<TaskLog> taskLogs = taskLogMapper.selectList(
                        new LambdaQueryWrapper<TaskLog>()
                                .eq(TaskLog::getRunId, resultMessage.getTaskRunId())
                                .orderByDesc(TaskLog::getCreateTime)
                );
                
                TaskLog existingLog = taskLogs.isEmpty() ? null : taskLogs.get(0);
                
                if (existingLog != null) {
                    existingLog.setStdoutPath(resultMessage.getLogPath());
                    existingLog.setStderrPath(resultMessage.getLogPath());
                    existingLog.setShardBatchId(resultMessage.getShardBatchId());
                    existingLog.setShardIndex(resultMessage.getShardIndex());
                    existingLog.setShardTotal(resultMessage.getShardTotal());
                    taskLogMapper.updateById(existingLog);
                    log.info("任务日志记录更新成功，TaskRunID: {}", resultMessage.getTaskRunId());
                } else {
                    TaskLog taskLog = TaskLog.builder()
                            .runId(resultMessage.getTaskRunId())
                            .stdoutPath(resultMessage.getLogPath())
                            .stderrPath(resultMessage.getLogPath())
                            .shardBatchId(resultMessage.getShardBatchId())
                            .shardIndex(resultMessage.getShardIndex())
                            .shardTotal(resultMessage.getShardTotal())
                            .createTime(LocalDateTime.now())
                            .build();
                    taskLogMapper.insert(taskLog);
                    log.info("任务日志记录插入成功，TaskRunID: {}", resultMessage.getTaskRunId());
                }
            } catch (Exception e) {
                log.error("日志记录保存失败，TaskRunID: {}, Error: {}", 
                        resultMessage.getTaskRunId(), e.getMessage(), e);
            }
        }
    }
}