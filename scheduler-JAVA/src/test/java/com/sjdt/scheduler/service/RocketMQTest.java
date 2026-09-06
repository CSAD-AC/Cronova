package com.sjdt.scheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.Tag;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RocketMQ 生产/消费集成测试（真实连接 MQ，不走 Spring 上下文）
 * <p>
 * 验证 TASK_EXECUTE 主题的 Tag 定向投递（阶段二核心行为的黑盒验证）。
 * 属于<b>环境依赖型</b>测试：需要 RocketMQ name server 可达
 * （地址取环境变量 MQ_NAMESRV_ADDR，未设置回退 127.0.0.1:9876）。
 * 启动前用 TCP 探测做环境守卫——MQ 不可达时整类用例标记 SKIPPED（黄色跳过），
 * 而非误报 FAILURE；MQ 在位（如 Docker 中间件已启动）时正常执行。
 */
class RocketMQTest {

    // MQ 地址通过环境变量 MQ_NAMESRV_ADDR 注入，未设置时回退本地默认（真实地址见 .env，不入库）
    private static final String NAMESRV_ADDR =
            System.getenv().getOrDefault("MQ_NAMESRV_ADDR", "127.0.0.1:9876");
    private static final String TOPIC = "TASK_EXECUTE";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 环境守卫：name server 任一地址 TCP 可达才执行，否则整类跳过。
     * RocketMQ 地址可能分号分隔多个（host1:port;host2:port），逐个探测。
     */
    @BeforeAll
    static void requireNameServerReachable() {
        boolean reachable = false;
        for (String addr : NAMESRV_ADDR.split(";")) {
            if (isReachable(addr.trim())) {
                reachable = true;
                break;
            }
        }
        Assumptions.assumeTrue(reachable,
                "RocketMQ name server 不可达，跳过集成测试（启动 MQ 后重跑，或经 MQ_NAMESRV_ADDR 指定地址）: "
                        + NAMESRV_ADDR);
    }

    /**
     * TCP 探测地址可达性（1 秒超时，探测失败视为不可达）。
     *
     * @param hostPort host:port 形式的地址
     * @return true=可建立 TCP 连接
     */
    private static boolean isReachable(String hostPort) {
        String[] parts = hostPort.split(":");
        if (parts.length != 2) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])), 1000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 测试：公共任务（TASK_EXECUTE:PUBLIC_TASK）
     */
    @Test
    void testPublicTaskProduceAndConsume() throws Exception {

        String testId = UUID.randomUUID().toString();
        String tag = "PUBLIC_TASK";
        String consumerGroup = "TEST_CONSUMER_" + testId;
        String producerGroup = "TEST_PRODUCER_" + testId;

        CountDownLatch latch = new CountDownLatch(1);

        // =======================
        // 1️⃣ Producer 先启动并发送（触发自动建 Topic）
        // =======================
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(NAMESRV_ADDR);
        producer.start();

        TaskExecuteMessage message = buildTaskExecuteMessage(testId, null);
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(message);

        Message mqMessage = new Message(TOPIC, tag, body);
        producer.send(mqMessage);

        System.out.println("[Producer] 已发送公共任务，taskId=" + message.getTaskId());

        // =======================
        // 2️⃣ Consumer 再启动（从最新 offset）
        // =======================
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(NAMESRV_ADDR);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(TOPIC, tag);

        consumer.registerMessageListener(
                (MessageListenerConcurrently) (msgs, context) -> {
                    for (MessageExt msg : msgs) {
                        String bodyStr = new String(msg.getBody(), StandardCharsets.UTF_8);
                        System.out.println("[Consumer] 收到消息: " + bodyStr);

                        if (bodyStr.contains(testId)) {
                            latch.countDown();
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
        );

        consumer.start();

        // =======================
        // 3️⃣ Assert
        // =======================
        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "未在指定时间内收到公共任务消息");

        producer.shutdown();
        consumer.shutdown();
    }

    /**
     * 测试：定向任务（TASK_EXECUTE:TARGET_TASK）
     */
    @Test
    void testTargetTaskProduceAndConsume() throws Exception {

        String testId = UUID.randomUUID().toString();
        String tag = "TARGET_TASK";
        String consumerGroup = "TEST_CONSUMER_" + testId;
        String producerGroup = "TEST_PRODUCER_" + testId;

        CountDownLatch latch = new CountDownLatch(1);

        // ===== Producer =====
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(NAMESRV_ADDR);
        producer.start();

        TaskExecuteMessage message = buildTaskExecuteMessage(testId, "worker-001");
        byte[] body = OBJECT_MAPPER.writeValueAsBytes(message);

        Message mqMessage = new Message(TOPIC, tag, body);
        producer.send(mqMessage);

        System.out.println("[Producer] 已发送定向任务，taskId=" + message.getTaskId());

        // ===== Consumer =====
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(NAMESRV_ADDR);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.subscribe(TOPIC, tag);

        consumer.registerMessageListener(
                (MessageListenerConcurrently) (msgs, context) -> {
                    for (MessageExt msg : msgs) {
                        String bodyStr = new String(msg.getBody(), StandardCharsets.UTF_8);
                        System.out.println("[Consumer] 收到消息: " + bodyStr);

                        if (bodyStr.contains(testId)) {
                            latch.countDown();
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
        );

        consumer.start();

        boolean received = latch.await(10, TimeUnit.SECONDS);
        assertTrue(received, "未在指定时间内收到定向任务消息");

        producer.shutdown();
        consumer.shutdown();
    }

    // =========================
    // 构造测试消息
    // =========================
    private TaskExecuteMessage buildTaskExecuteMessage(String testId, String targetWorkerId) {

        TaskExecuteMessage.ScriptInfo script = TaskExecuteMessage.ScriptInfo.builder()
                .id("script-" + testId)
                .name("Test Script")
                .path("/tmp/test.sh")
                .tags(Set.of(
                        Tag.builder()
                                .id(1)
                                .name("Java17")
                                .description("Java 17运行环境")
                                .build()
                ))
                .build();

        return TaskExecuteMessage.builder()
                .taskId("task-" + testId)
                .taskName("Test Task " + testId)
                .description("integration test")
                .scriptType("java")
                .script(script)
                .taskType("once")
                .timeoutSeconds(60)
                .targetWorkerId(targetWorkerId)
                .build();
    }
}
