package com.cronova.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HTTP回调执行记录实体
 * 记录HTTP回调任务的执行详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("callback_job_runs")
public class CallbackJobRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 执行记录ID */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联任务ID */
    @TableField(value = "task_id")
    private String taskId;

    /** 关联配置ID */
    @TableField(value = "config_id")
    private String configId;

    /** 请求ID（X-Request-Id），用于请求追踪 */
    @TableField(value = "request_id")
    private String requestId;

    /** 执行状态：pending/running/success/failed/timeout */
    private String status;

    /** 实际请求URL */
    @TableField(value = "request_url")
    private String requestUrl;

    /** 实际请求方法 */
    @TableField(value = "request_method")
    private String requestMethod;

    /** 请求数据MinIO路径（包含headers和body） */
    @TableField(value = "request_path")
    private String requestPath;

    /** 响应数据MinIO路径（包含statusCode、headers和body） */
    @TableField(value = "response_path")
    private String responsePath;

    /** 响应状态码 */
    @TableField(value = "response_status_code")
    private Integer responseStatusCode;

    /** 响应时间（毫秒） */
    @TableField(value = "response_time_ms")
    private Integer responseTimeMs;

    /** 重试次数索引（0表示首次执行） */
    @TableField(value = "retry_index")
    private Integer retryIndex;

    /** 开始时间 */
    @TableField(value = "start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField(value = "end_time")
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    @TableField(value = "duration_ms")
    private Integer durationMs;

    /** 错误信息 */
    @TableField(value = "error_message")
    private String errorMessage;

    /** 创建时间 */
    @TableField(value = "create_time")
    private LocalDateTime createTime;
}
