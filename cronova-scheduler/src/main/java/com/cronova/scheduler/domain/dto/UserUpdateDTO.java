package com.cronova.scheduler.domain.dto;

import lombok.Data;

/**
 * 用户更新请求DTO
 */
@Data
public class UserUpdateDTO {
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 密码（可选）
     */
    private String password;
}