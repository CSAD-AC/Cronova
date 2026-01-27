package com.sjdt.scheduler.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回调任务配置创建DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "回调任务配置创建DTO")
public class CallbackJobConfigCreateDTO {

    /** 任务ID */
    @Schema(description = "关联的任务ID", example = "task-001")
    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /** 回调URL */
    @Schema(description = "回调URL", example = "https://api.example.com/callback")
    @NotBlank(message = "回调URL不能为空")
    private String callbackUrl;

    /** 回调方法 */
    @Schema(description = "回调方法：GET/POST/PUT/DELETE", example = "POST")
    @Pattern(regexp = "^(GET|POST|PUT|DELETE)$", message = "回调方法必须是GET/POST/PUT/DELETE之一")
    private String callbackMethod;

    /** 认证秘钥（用于 X-Secret 请求头） */
    @Schema(description = "认证秘钥（用于 X-Secret 请求头）", example = "my-secret-key")
    private String secretKey;

}
