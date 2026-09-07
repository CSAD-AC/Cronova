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
 * HTTP回调任务配置实体
 * 存储HTTP回调任务的配置信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("callback_job_configs")
public class CallbackJobConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 配置ID */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联任务ID */
    @TableField(value = "task_id")
    private String taskId;

    /** 回调URL */
    @TableField(value = "callback_url")
    private String callbackUrl;

    /** 回调方法：GET/POST/PUT/DELETE */
    @TableField(value = "callback_method")
    private String callbackMethod;

    /** 认证秘钥（用于 X-Secret 请求头） */
    @TableField(value = "secret_key")
    private String secretKey;

    /** 上次回调时间 */
    @TableField(value = "last_callback_time")
    private LocalDateTime lastCallbackTime;

    /** 上次回调状态：success/failed/timeout */
    @TableField(value = "last_callback_status")
    private String lastCallbackStatus;

    /** 总回调次数 */
    @TableField(value = "total_callback_count")
    private Integer totalCallbackCount;

    /** 成功次数 */
    @TableField(value = "success_count")
    private Integer successCount;

    /** 失败次数 */
    @TableField(value = "failed_count")
    private Integer failedCount;

    /** 创建人 */
    @TableField(value = "created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;
}
