package com.sjdt.scheduler.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.support.DefaultRocketMQListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RocketMQ 启动信息日志输出器。
 *
 * <p>用途：应用启动完成后打印 RocketMQ 的 NameServer、Producer 组、Consumer 组，
 * 以及每个消息监听器的实际订阅信息（消费组 / 主题 / Tag）。</p>
 *
 * <p>注意：@RocketMQMessageListener 注解中显式声明的 consumerGroup 会覆盖
 * spring 配置（rocketmq.consumer.group），本日志输出的"监听器消费组"即进程
 * 实际生效的组名，便于核对多环境隔离（独立消费组）是否真正生效。</p>
 */
@Slf4j
@Component
public class RocketMqStartupLogRunner implements ApplicationRunner {

    /** 配置中的 NameServer（参考值） */
    @Value("${rocketmq.name-server:}")
    private String configuredNameServer;

    /** 配置中的生产者组（参考值） */
    @Value("${rocketmq.producer.group:}")
    private String configuredProducerGroup;

    /** 配置中的消费者组（参考值，可能被监听器注解覆盖） */
    @Value("${rocketmq.consumer.group:}")
    private String configuredConsumerGroup;

    /** 所有 RocketMQ 消息监听器容器（每个 @RocketMQMessageListener 对应一个） */
    private final List<DefaultRocketMQListenerContainer> listenerContainers;

    /**
     * 构造注入监听器容器列表。
     *
     * @param listenerContainers Spring 容器中全部 RocketMQ 监听器容器（反映实际生效配置）
     */
    public RocketMqStartupLogRunner(List<DefaultRocketMQListenerContainer> listenerContainers) {
        this.listenerContainers = listenerContainers;
    }

    /**
     * 应用启动完成后输出 RocketMQ 消费与订阅信息日志。
     *
     * @param args 启动参数（本方法不使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("========== RocketMQ 启动信息开始 ==========");
        log.info("配置 NameServer: {}", configuredNameServer);
        log.info("配置 ProducerGroup: {}", configuredProducerGroup);
        log.info("配置 ConsumerGroup(通用): {}", configuredConsumerGroup);
        if (listenerContainers == null || listenerContainers.isEmpty()) {
            log.warn("未发现任何 RocketMQ 消息监听器");
        } else {
            for (DefaultRocketMQListenerContainer container : listenerContainers) {
                log.info("监听器 -> 消费组: {}, 主题: {}, Tag: {}, NameServer: {}",
                        container.getConsumerGroup(),
                        container.getTopic(),
                        container.getSelectorExpression(),
                        container.getNameServer());
            }
        }
        log.info("========== RocketMQ 启动信息结束 ==========");
    }
}
