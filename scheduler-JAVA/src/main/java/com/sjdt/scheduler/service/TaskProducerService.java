package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.message.TaskDeleteMessage;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.message.TaskPauseMessage;
import com.sjdt.scheduler.domain.message.TaskResumeMessage;
import com.sjdt.scheduler.domain.message.TaskStopMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 任务生产者服务
 * 负责将任务发送到RocketMQ
 */
@Slf4j
@Service
public class TaskProducerService {

    private final RocketMQTemplate rocketMQTemplate;
    private final TaskControlProducerService taskControlProducerService;

    @Autowired
    public TaskProducerService(RocketMQTemplate rocketMQTemplate, TaskControlProducerService taskControlProducerService) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.taskControlProducerService = taskControlProducerService;
    }

    /**
     * 发送任务执行消息
     *
     * @param message 任务执行消息
     */
    public void sendTask(TaskExecuteMessage message) {
        try {
            rocketMQTemplate.convertAndSend("TASK_EXECUTE", message);
            log.info("发送任务消息成功，TaskID: {}, RunID: {}", message.getTaskId(), message.getTaskRunId());
        } catch (Exception e) {
            log.error("发送任务消息失败，TaskID: {}", message.getTaskId(), e);
            throw new RuntimeException("发送任务消息失败", e);
        }
    }

    /**
     * 发送延时任务执行消息
     *
     * @param message 任务执行消息
     * @param delayLevel 延时级别 (1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h)
     */
    public void sendTaskWithDelay(TaskExecuteMessage message, int delayLevel) {
        try {
            rocketMQTemplate.syncSend("TASK_EXECUTE", 
                    MessageBuilder.withPayload(message).build(), 
                    3000, 
                    delayLevel);
            log.info("发送延时任务消息成功，TaskID: {}, RunID: {}, DelayLevel: {}", 
                    message.getTaskId(), message.getTaskRunId(), delayLevel);
        } catch (Exception e) {
            log.error("发送延时任务消息失败，TaskID: {}", message.getTaskId(), e);
            throw new RuntimeException("发送延时任务消息失败", e);
        }
    }

    /**
     * 发送任务控制消息（停止/暂停等）
     * 修正：委派给专门的 TaskControlProducerService 处理，确保使用正确的 Topic 和 Tag
     *
     * @param message 控制消息
     */
    public void sendTaskControl(Object message) {
        try {
            if (message instanceof TaskStopMessage) {
                taskControlProducerService.sendStopTask((TaskStopMessage) message);
            } else if (message instanceof TaskPauseMessage) {
                taskControlProducerService.sendPauseTask((TaskPauseMessage) message);
            } else if (message instanceof TaskResumeMessage) {
                taskControlProducerService.sendResumeTask((TaskResumeMessage) message);
            } else if (message instanceof TaskDeleteMessage) {
                taskControlProducerService.sendDeleteTask((TaskDeleteMessage) message);
            } else {
                log.warn("不支持的控制消息类型: {}", message != null ? message.getClass().getName() : "null");
                return;
            }
            log.info("发送控制消息成功，Type: {}", message.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("发送控制消息失败", e);
        }
    }
}