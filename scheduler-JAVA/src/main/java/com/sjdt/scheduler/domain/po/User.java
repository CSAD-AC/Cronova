package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * 用户表持久化对象 (PO)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

     /** 用户ID (UUID) */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 密码 */
    private String password;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
