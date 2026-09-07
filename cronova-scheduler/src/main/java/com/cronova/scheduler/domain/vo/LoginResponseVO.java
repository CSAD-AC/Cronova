package com.cronova.scheduler.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录响应VO
 */
@Data
@Builder
public class LoginResponseVO {
    /**
     * 用户信息
     */
    private UserVO user;
    
    /**
     * 登录令牌
     */
    private String token;
}