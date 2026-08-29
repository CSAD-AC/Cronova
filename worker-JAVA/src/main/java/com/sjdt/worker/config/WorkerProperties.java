package com.sjdt.worker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 执行器配置（application.yml + 环境变量占位符）
 */
@Data
@ConfigurationProperties(prefix = "worker")
public class WorkerProperties {

    /** 调度器地址 */
    private String schedulerBaseUrl;

    /** 本地配置路径（WorkerID / AK / SK 持久化文件） */
    private String localConfigPath;

    /** 脚本下载目录 */
    private String scriptsDir;

    /** 任务实例数据目录 */
    private String instanceDir;

    /** Worker 标签（逗号分隔，可选） */
    private String workerTags = "";

    /** 心跳间隔（秒） */
    private int heartbeatIntervalSec = 2;

    /** 系统状态上报间隔（秒） */
    private int statusReportIntervalSec = 10;

    /** 运行中任务状态上报间隔（秒） */
    private int taskStatusIntervalSec = 5;

    /** 标签变化检测间隔（秒） */
    private int tagCheckIntervalSec = 60;

    /** 任务执行线程池大小 */
    private int executorThreads = 12;

    /** RocketMQ 配置 */
    private final Mq mq = new Mq();

    /** MinIO 配置 */
    private final Minio minio = new Minio();

    @Data
    public static class Mq {
        private String namesrvAddr;
        private String consumerGroup;
        private String producerGroup;
        private String taskTopic;
        private String taskResultTopic;
        private String taskControlTopic;
        private int sendMsgTimeoutMs = 5000;
    }

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private boolean secure;
        private String bucket;
    }
}
