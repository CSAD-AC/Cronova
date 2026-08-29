package com.sjdt.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Java 执行器启动入口
 * <p>
 * 非 Web 应用：不提供 HTTP 服务，通过 MQ 消费任务、向调度器上报状态。
 * 进程由 RocketMQ 消费者线程保持存活，退出时经 Spring 生命周期回调优雅清理。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
