package com.sjdt.scheduler.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 回调任务配置更新DTO
 */
@Data
@Schema(description = "回调任务配置更新DTO")
public class CallbackJobConfigUpdateDTO {

    /** 配置ID */
    @Schema(description = "配置ID", example = "config-001")
    private String id;

    /** 关联任务ID */
    @Schema(description = "关联任务ID", example = "task-001")
    private String taskId;

    /** 回调URL */
    @Schema(description = "回调URL", example = "http://example.com/callback")
    private String callbackUrl;

    /** 回调方法：GET/POST/PUT/DELETE */
    @Schema(description = "回调方法：GET/POST/PUT/DELETE", example = "POST")
    private String callbackMethod;

    /** 认证秘钥（用于 X-Secret 请求头） */
    @Schema(description = "认证秘钥（用于 X-Secret 请求头）", example = "your-secret-key")
    private String secretKey;

    /** 超时时间（秒） */
    @Schema(description = "超时时间（秒）", example = "30")
    @Positive(message = "超时时间必须为正数")
    private Integer timeoutSeconds;

    /** 重试次数 */
    @Schema(description = "重试次数", example = "0")
    @Positive(message = "重试次数必须为正数或0")
    private Integer retryCount;

    /** 重试间隔（秒） */
    @Schema(description = "重试间隔（秒）", example = "60")
    @Positive(message = "重试间隔必须为正数")
    private Integer retryIntervalSeconds;
}
