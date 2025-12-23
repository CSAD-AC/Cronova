package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskResultMessage;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
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
        // 创建或更新任务运行实例
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

        // 保存任务执行结果
        taskRunMapper.insert(taskRun);
        
        // 保存日志路径到task_log表
        if (resultMessage.getLogPath() != null && !resultMessage.getLogPath().isEmpty()) {
            TaskLog taskLog = TaskLog.builder()
                    .runId(resultMessage.getTaskRunId())
                    .stdoutPath(resultMessage.getLogPath())
                    .stderrPath(resultMessage.getLogPath()) // 目前只支持单个日志路径，stdout和stderr使用相同路径
                    .createTime(LocalDateTime.now())
                    .build();
            
            taskLogMapper.insert(taskLog);
        }
    }
}