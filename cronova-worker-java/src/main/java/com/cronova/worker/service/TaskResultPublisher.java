package com.cronova.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cronova.worker.config.WorkerProperties;
import com.cronova.worker.model.TaskResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 任务结果发布（发送到 TASK_RESULT 主题）
 */
@Slf4j
@Component
public class TaskResultPublisher {

    private final WorkerProperties properties;
    private final ObjectMapper objectMapper;
    private volatile DefaultMQProducer producer;

    public TaskResultPublisher(WorkerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** 注册成功后初始化生产者 */
    public synchronized void start() {
        if (producer != null) {
            return;
        }
        DefaultMQProducer p = new DefaultMQProducer(properties.getMq().getProducerGroup());
        p.setNamesrvAddr(properties.getMq().getNamesrvAddr());
        p.setSendMsgTimeout(properties.getMq().getSendMsgTimeoutMs());
        try {
            p.start();
            this.producer = p;
            log.info("RocketMQ 结果生产者已启动，group={}, topic={}",
                    properties.getMq().getProducerGroup(), properties.getMq().getTaskResultTopic());
        } catch (Exception e) {
            log.error("RocketMQ 结果生产者启动失败", e);
        }
    }

    /** 发送任务结果（失败记录日志，不抛出，避免影响任务主流程） */
    public void publish(TaskResultMessage result) {
        DefaultMQProducer p = producer;
        if (p == null) {
            log.warn("结果生产者未初始化，结果丢弃，taskRunId={}", result.getTaskRunId());
            return;
        }
        try {
            Message msg = new Message();
            msg.setTopic(properties.getMq().getTaskResultTopic());
            msg.setKeys(result.getTaskId());
            msg.setBody(objectMapper.writeValueAsBytes(result));
            p.send(msg);
            log.debug("任务结果已发送，taskId={}, taskRunId={}, status={}",
                    result.getTaskId(), result.getTaskRunId(), result.getStatus());
        } catch (Exception e) {
            log.error("任务结果发送失败，taskRunId={}, err={}", result.getTaskRunId(), e.getMessage(), e);
        }
    }

    public synchronized void shutdown() {
        if (producer != null) {
            producer.shutdown();
            producer = null;
            log.info("RocketMQ 结果生产者已关闭");
        }
    }
}
