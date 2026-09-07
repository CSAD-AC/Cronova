package com.sjdt.scheduler.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户登录DTO
 */
@Data
@Schema(description = "用户登录请求参数")
public class UserLoginDTO {
    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "username")
    private String username;
    
    /**
     * 密码
     */
    @Schema(description = "密码", example = "password")
    private String password;
}