package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * RocketMQ生产者服务
 * 负责向Worker发送任务执行消息
 */
@Service
public class TaskProducerService {

    /** RocketMQ模板 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 任务执行Topic */
    private static final String TOPIC_TASK_EXECUTE = "TASK_EXECUTE";

    /** 公共任务Tag */
    private static final String TAG_PUBLIC_TASK = "PUBLIC_TASK";

    /** 定向任务Tag */
    private static final String TAG_TARGET_TASK = "TARGET_TASK";

    @Autowired
    public TaskProducerService(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送公共任务
     * 公共任务会被RocketMQ分配给一个Worker，Worker可以拒绝执行并重新分配
     *
     * @param message 任务执行消息
     */
    public void sendPublicTask(TaskExecuteMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_EXECUTE, TAG_PUBLIC_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }

    /**
     * 发送定向任务
     * 定向任务会被发送给指定的Worker，只有目标Worker会执行
     *
     * @param message 任务执行消息
     */
    public void sendTargetTask(TaskExecuteMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_EXECUTE, TAG_TARGET_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }

    /**
     * 发送任务（根据是否有目标Worker自动选择发送方式）
     *
     * @param message 任务执行消息
     */
    public void sendTask(TaskExecuteMessage message) {
        if (message.getTargetWorkerId() != null && !message.getTargetWorkerId().isEmpty()) {
            sendTargetTask(message);
        } else {
            sendPublicTask(message);
        }
    }
}