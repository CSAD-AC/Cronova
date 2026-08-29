package com.sjdt.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.worker.common.LocalConfigStore;
import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.config.WorkerProperties;
import com.sjdt.worker.model.TaskControlMessage;
import com.sjdt.worker.model.TaskExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * RocketMQ 消费者/生产者管理（注册成功后启动）
 * <p>
 * 与 Python 执行器 mq_consumer 对齐：
 * <ul>
 *   <li>任务消费者：独立消费组 {GROUP}_{workerId}，订阅 TASK_EXECUTE，Tag=workerId（Broker 硬过滤定向投递）；</li>
 *   <li>控制消费者：独立消费组 {GROUP}_CONTROL，广播模式，订阅 TASK_CONTROL，Tag=*；</li>
 *   <li>结果生产者：{GROUP}，发送 TASK_RESULT。</li>
 * </ul>
 */
@Slf4j
@Component
public class MqManager {

    private final WorkerProperties properties;
    private final WorkerContext workerContext;
    private final ObjectMapper objectMapper;
    private final TaskService taskService;
    private final LocalConfigStore localConfigStore;
    private final TagReporter tagReporter;

    private volatile DefaultMQPushConsumer taskConsumer;
    private volatile DefaultMQPushConsumer controlConsumer;

    public MqManager(WorkerProperties properties,
                     WorkerContext workerContext,
                     ObjectMapper objectMapper,
                     TaskService taskService,
                     LocalConfigStore localConfigStore,
                     TagReporter tagReporter) {
        this.properties = properties;
        this.workerContext = workerContext;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
        this.localConfigStore = localConfigStore;
        this.tagReporter = tagReporter;
    }

    /** 注册完成后启动消费者 */
    public synchronized void start() {
        if (!workerContext.isRegistered()) {
            log.error("Worker 未注册，无法启动 MQ 消费者");
            return;
        }
        startTaskConsumer();
        startControlConsumer();
    }

    private void startTaskConsumer() {
        if (taskConsumer != null) {
            return;
        }
        String group = properties.getMq().getConsumerGroup() + "_" + workerContext.getWorkerId();
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(properties.getMq().getNamesrvAddr());
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(4);
        consumer.setConsumeThreadMax(8);
        try {
            consumer.subscribe(properties.getMq().getTaskTopic(), workerContext.getWorkerId());
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext ctx) {
                    for (MessageExt msg : msgs) {
                        if (!handleTaskMessage(msg)) {
                            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });
            consumer.start();
            this.taskConsumer = consumer;
            log.info("任务消费者已启动，group={}, topic={}, tag={}",
                    group, properties.getMq().getTaskTopic(), workerContext.getWorkerId());
        } catch (Exception e) {
            log.error("任务消费者启动失败，group={}", group, e);
        }
    }

    private void startControlConsumer() {
        if (controlConsumer != null) {
            return;
        }
        String group = properties.getMq().getConsumerGroup() + "_CONTROL";
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(properties.getMq().getNamesrvAddr());
        consumer.setMessageModel(MessageModel.BROADCASTING);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setConsumeThreadMin(2);
        consumer.setConsumeThreadMax(4);
        try {
            consumer.subscribe(properties.getMq().getTaskControlTopic(), "*");
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext ctx) {
                    for (MessageExt msg : msgs) {
                        handleControlMessage(msg);
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });
            consumer.start();
            this.controlConsumer = consumer;
            log.info("控制消费者已启动（广播），group={}, topic={}", group, properties.getMq().getTaskControlTopic());
        } catch (Exception e) {
            log.error("控制消费者启动失败，group={}", group, e);
        }
    }

    private boolean handleTaskMessage(MessageExt msg) {
        if (!workerContext.isRegistered()) {
            log.warn("Worker 未注册，拒绝处理任务消息");
            return true;
        }
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            TaskExecuteMessage task = objectMapper.readValue(body, TaskExecuteMessage.class);
            log.info("收到任务消息 - MsgID: {}, TaskID: {}, TaskRunID: {}, ScriptType: {}",
                    msg.getMsgId(), task.getTaskId(), task.getTaskRunId(), task.getScriptType());
            return taskService.processTask(task);
        } catch (Exception e) {
            log.error("任务消息解析失败，MsgID: {}, err: {}", msg.getMsgId(), e.getMessage(), e);
            return false;
        }
    }

    private void handleControlMessage(MessageExt msg) {
        if (!workerContext.isRegistered()) {
            log.warn("Worker 未注册，拒绝处理控制消息");
            return;
        }
        String tag = msg.getTags();
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            TaskControlMessage control = objectMapper.readValue(body, TaskControlMessage.class);

            if ("UPDATE_TAGS".equals(tag)) {
                handleTagUpdate(control);
                return;
            }

            // 定向控制：目标 Worker 不匹配则跳过
            if (control.getTargetWorkerId() != null && !control.getTargetWorkerId().isBlank()
                    && !control.getTargetWorkerId().equals(workerContext.getWorkerId())) {
                log.debug("控制请求定向到 Worker {}，当前为 {}，跳过，tag={}",
                        control.getTargetWorkerId(), workerContext.getWorkerId(), tag);
                return;
            }

            boolean success;
            switch (tag) {
                case "STOP_TASK" -> success = taskService.stopTask(control);
                case "KILL_TASK" -> success = taskService.killTask(control);
                case "PAUSE_TASK" -> success = taskService.pauseTask(control);
                case "RESUME_TASK" -> success = taskService.resumeTask(control);
                case "DELETE_TASK" -> success = taskService.deleteTask(control);
                default -> {
                    log.error("未知的任务控制类型：{}", tag);
                    return;
                }
            }
            if (success) {
                log.info("任务控制操作成功，tag={}, taskRunId={}", tag, control.getTaskRunId());
            } else {
                log.error("任务控制操作失败，tag={}, taskRunId={}", tag, control.getTaskRunId());
            }
        } catch (Exception e) {
            log.error("控制消息处理失败，MsgID: {}, tag: {}, err: {}", msg.getMsgId(), tag, e.getMessage(), e);
        }
    }

    private void handleTagUpdate(TaskControlMessage control) {
        if (control.getWorkerId() != null && !control.getWorkerId().isBlank()
                && !control.getWorkerId().equals(workerContext.getWorkerId())) {
            log.info("标签更新请求定向到 Worker {}，当前为 {}，跳过",
                    control.getWorkerId(), workerContext.getWorkerId());
            return;
        }
        List<String> tags = control.getTags();
        workerContext.updateTags(tags);
        tagReporter.initReportedTags(tags);
        LocalConfigStore.LocalData data = localConfigStore.load();
        data.setWorkerTags(tags);
        localConfigStore.save(data);
        log.info("标签更新成功，tags={}", tags);
    }

    public synchronized void shutdown() {
        if (taskConsumer != null) {
            try {
                taskConsumer.shutdown();
            } catch (Exception e) {
                log.warn("关闭任务消费者失败：{}", e.getMessage());
            }
            taskConsumer = null;
        }
        if (controlConsumer != null) {
            try {
                controlConsumer.shutdown();
            } catch (Exception e) {
                log.warn("关闭控制消费者失败：{}", e.getMessage());
            }
            controlConsumer = null;
        }
    }
}
