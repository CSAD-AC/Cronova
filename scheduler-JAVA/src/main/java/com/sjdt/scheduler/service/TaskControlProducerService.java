package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.message.TaskPauseMessage;
import com.sjdt.scheduler.domain.message.TaskResumeMessage;
import com.sjdt.scheduler.domain.message.TaskStopMessage;
import com.sjdt.scheduler.domain.message.TaskDeleteMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 任务控制消息生产者服务
 * 负责向Worker发送任务控制消息（暂停、恢复、停止、删除、强制终止）
 */
@Service
public class TaskControlProducerService {

    /** RocketMQ模板 */
    private final RocketMQTemplate rocketMQTemplate;

    /** 任务控制Topic */
    private static final String TOPIC_TASK_CONTROL = "TASK_CONTROL";

    /** 控制消息Tag */
    private static final String TAG_STOP_TASK = "STOP_TASK";
    private static final String TAG_PAUSE_TASK = "PAUSE_TASK";
    private static final String TAG_RESUME_TASK = "RESUME_TASK";
    private static final String TAG_DELETE_TASK = "DELETE_TASK";
    private static final String TAG_KILL_TASK = "KILL_TASK";

    @Autowired
    public TaskControlProducerService(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送停止任务消息（SIGTERM信号）
     *
     * @param message 停止任务消息
     */
    public void sendStopTask(TaskStopMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_CONTROL, TAG_STOP_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }

    /**
     * 发送暂停任务消息
     *
     * @param message 暂停任务消息
     */
    public void sendPauseTask(TaskPauseMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_CONTROL, TAG_PAUSE_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }

    /**
     * 发送恢复任务消息
     *
     * @param message 恢复任务消息
     */
    public void sendResumeTask(TaskResumeMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_CONTROL, TAG_RESUME_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }

    /**
     * 发送删除任务消息
     *
     * @param message 删除任务消息
     */
    public void sendDeleteTask(TaskDeleteMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_CONTROL, TAG_DELETE_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }

    /**
     * 发送强制终止任务消息（SIGKILL信号）
     *
     * @param message 停止任务消息（用于强制终止）
     */
    public void sendKillTask(TaskStopMessage message) {
        String destination = String.format("%s:%s", TOPIC_TASK_CONTROL, TAG_KILL_TASK);
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(message).build());
    }
}
