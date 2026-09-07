package com.cronova.scheduler.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户注册DTO
 */
@Data
@Schema(description = "用户注册请求参数")
public class UserRegisterDTO {
    /**
     * 用户名
     */
    @Schema(description = "用户名", example = "username")
    private String username;

    @Schema(description = "昵称", example = "测试用户")
    private String nickname;
    /**
     * 密码
     */
    @Schema(description = "密码", example = "password")
    private String password;
}