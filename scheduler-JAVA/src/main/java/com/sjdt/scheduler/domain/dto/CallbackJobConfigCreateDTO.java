package com.sjdt.scheduler.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * 回调任务配置创建DTO
 */
@Data
@Schema(description = "回调任务配置创建DTO")
public class CallbackJobConfigCreateDTO {

    /** 关联任务ID */
    @Schema(description = "关联任务ID", example = "task-001")
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /** 回调URL */
    @Schema(description = "回调URL", example = "http://example.com/callback")
    @NotBlank(message = "回调URL不能为空")
    private String callbackUrl;

    /** 回调方法：GET/POST/PUT/DELETE */
    @Schema(description = "回调方法：GET/POST/PUT/DELETE", example = "POST")
    @NotBlank(message = "回调方法不能为空")
    private String callbackMethod;

    /** 认证秘钥（用于 X-Secret 请求头） */
    @Schema(description = "认证秘钥（用于 X-Secret 请求头）", example = "your-secret-key")
    @NotBlank(message = "密钥不能为空")
    private String secretKey;

    /** 超时时间（秒） */
    @Schema(description = "超时时间（秒），默认30", example = "30")
    @NotNull(message = "超时时间不能为空")
    @Positive(message = "超时时间必须为正数")
    private Integer timeoutSeconds;

    /** 重试次数 */
    @Schema(description = "重试次数，默认0", example = "0")
    @NotNull(message = "重试次数不能为空")
    @Positive(message = "重试次数必须为正数或0")
    private Integer retryCount;

    /** 重试间隔（秒） */
    @Schema(description = "重试间隔（秒），默认60", example = "60")
    @NotNull(message = "重试间隔不能为空")
    @Positive(message = "重试间隔必须为正数")
    private Integer retryIntervalSeconds;
}
