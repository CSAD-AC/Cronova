package com.sjdt.scheduler.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户VO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {
    /**
     * 用户ID
     */
    private String id;
    
    /**
     * 用户名
     */
    private String username;

    /** 昵称 */
    private String nickname;
}