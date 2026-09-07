package com.cronova.scheduler.domain.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 回调任务执行消息
 * 用于调度器向回调执行器传递HTTP回调任务执行信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallbackJobExecuteMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 任务运行实例ID */
    private String taskRunId;

    /** 回调配置ID */
    private String configId;

    /** 回调URL */
    private String callbackUrl;

    /** 回调方法：GET/POST/PUT/DELETE */
    private String callbackMethod;

    /** 认证密钥（用于X-Secret请求头） */
    private String secretKey;

    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** 重试次数 */
    private Integer retryCount;

    /** 重试间隔（秒） */
    private Integer retryIntervalSeconds;

    /** 任务执行结果（可为null） */
    private String taskResult;

    /** 请求数据（任务参数等） */
    private java.util.Map<String, Object> requestData;

    /** 触发时间 */
    private LocalDateTime triggerTime;
}
