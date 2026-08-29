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
 * <p>
 * 负责将任务发送到 RocketMQ，**按 WorkerID 打 Tag 定向投递**（Topic:Tag = TASK_EXECUTE:{workerId}）。
 * 定向投递保证：消息只会被目标 Worker 的消费组（expression=workerId）拉到，
 * 杜绝"集群模式同组一消息一消费者 + 非目标 Worker ACK 吞消息"的静默丢失。
 * 路由权收归调度侧——谁该执行由路由策略决定（见 TaskDispatchService）。
 */
@Slf4j
@Service
public class TaskProducerService {

    private static final String TASK_EXECUTE_TOPIC = "TASK_EXECUTE";

    private final RocketMQTemplate rocketMQTemplate;
    private final TaskControlProducerService taskControlProducerService;

    @Autowired
    public TaskProducerService(RocketMQTemplate rocketMQTemplate, TaskControlProducerService taskControlProducerService) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.taskControlProducerService = taskControlProducerService;
    }

    /**
     * 定向发送任务执行消息到指定 Worker（Tag = workerId）
     *
     * @param message  任务执行消息
     * @param workerId 目标 Worker ID
     */
    public void sendTask(TaskExecuteMessage message, String workerId) {
        try {
            rocketMQTemplate.syncSend(TASK_EXECUTE_TOPIC + ":" + workerId,
                    MessageBuilder.withPayload(message).build(),
                    3000);
            log.info("定向发送任务消息成功，TaskID: {}, RunID: {}, WorkerID: {}",
                    message.getTaskId(), message.getTaskRunId(), workerId);
        } catch (Exception e) {
            log.error("定向发送任务消息失败，TaskID: {}, WorkerID: {}", message.getTaskId(), workerId, e);
            throw new RuntimeException("定向发送任务消息失败", e);
        }
    }

    /**
     * 定向发送延时任务执行消息到指定 Worker（Tag = workerId）
     *
     * @param message    任务执行消息
     * @param workerId   目标 Worker ID
     * @param delayLevel 延时级别 (1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h)
     */
    public void sendTaskWithDelay(TaskExecuteMessage message, String workerId, int delayLevel) {
        try {
            rocketMQTemplate.syncSend(TASK_EXECUTE_TOPIC + ":" + workerId,
                    MessageBuilder.withPayload(message).build(),
                    3000,
                    delayLevel);
            log.info("定向发送延时任务消息成功，TaskID: {}, RunID: {}, WorkerID: {}, DelayLevel: {}",
                    message.getTaskId(), message.getTaskRunId(), workerId, delayLevel);
        } catch (Exception e) {
            log.error("定向发送延时任务消息失败，TaskID: {}, WorkerID: {}", message.getTaskId(), workerId, e);
            throw new RuntimeException("定向发送延时任务消息失败", e);
        }
    }

    /**
     * 发送任务控制消息（停止/暂停等）
     * 委派给专门的 TaskControlProducerService 处理，确保使用正确的 Topic 和 Tag
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
