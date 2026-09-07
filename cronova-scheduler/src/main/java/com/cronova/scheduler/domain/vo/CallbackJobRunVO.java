package com.cronova.scheduler.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回调执行记录视图对象
 * 用于展示回调执行记录的详细信息
 */
@Data
@Schema(description = "回调执行记录视图对象")
public class CallbackJobRunVO {

    /** 执行记录ID */
    @Schema(description = "执行记录ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    /** 关联任务ID */
    @Schema(description = "关联任务ID", example = "task-001")
    private String taskId;

    /** 关联配置ID */
    @Schema(description = "关联配置ID", example = "config-001")
    private String configId;

    /** 关联任务运行实例ID */
    @Schema(description = "关联任务运行实例ID", example = "run-001")
    private String taskRunId;

    /** 请求ID（X-Request-Id），用于请求追踪 */
    @Schema(description = "请求ID", example = "abc123def456")
    private String requestId;

    /** 执行状态：pending/running/success/failed/timeout */
    @Schema(description = "执行状态", example = "success", allowableValues = {"pending", "running", "success", "failed", "timeout"})
    private String status;

    /** 实际请求URL */
    @Schema(description = "实际请求URL", example = "https://api.example.com/callback")
    private String requestUrl;

    /** 实际请求方法 */
    @Schema(description = "实际请求方法", example = "POST")
    private String requestMethod;

    /** 请求数据MinIO路径（包含headers和body） */
    @Schema(description = "请求数据MinIO路径", example = "callback-data/2024/01/19/abc123-request.json")
    private String requestPath;

    /** 响应数据MinIO路径（包含statusCode、headers和body） */
    @Schema(description = "响应数据MinIO路径", example = "callback-data/2024/01/19/abc123-response.json")
    private String responsePath;

    /** 响应状态码 */
    @Schema(description = "响应状态码", example = "200")
    private Integer responseStatusCode;

    /** 响应时间（毫秒） */
    @Schema(description = "响应时间（毫秒）", example = "150")
    private Integer responseTimeMs;

    /** 重试次数索引（0表示首次执行） */
    @Schema(description = "重试次数索引", example = "0")
    private Integer retryIndex;

    /** 开始时间 */
    @Schema(description = "开始时间", example = "2024-01-19T10:30:00")
    private LocalDateTime startTime;

    /** 结束时间 */
    @Schema(description = "结束时间", example = "2024-01-19T10:30:00.150")
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    @Schema(description = "执行耗时（毫秒）", example = "150")
    private Integer durationMs;

    /** 错误信息 */
    @Schema(description = "错误信息", example = "Connection timeout")
    private String errorMessage;

    /** 创建时间 */
    @Schema(description = "创建时间", example = "2024-01-19T10:30:00")
    private LocalDateTime createTime;

    /** 任务名称（关联查询获取） */
    @Schema(description = "任务名称", example = "数据同步任务")
    private String taskName;

    /** 配置名称（关联查询获取） */
    @Schema(description = "配置名称", example = "主回调配置")
    private String configName;
}
