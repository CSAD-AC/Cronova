package com.sjdt.scheduler.domain.po;

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
 * 任务定义实体
 * 支持一次性任务（once）和定时任务（cron）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tasks")
public class Task implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 任务名称 */
    private String name;

    /** 脚本id */
    @TableField(value = "script_id")
    private String scriptId;

    /** 任务类型：once / cron */
    @TableField(value = "task_type")
    private String taskType;

    /**
     * Cron 表达式
     * 示例：0 0 * * *
     * 当 taskType = cron 时必须存在
     */
    @TableField(value = "cron_expr")
    private String cronExpr;

    /** 是否启用（主要用于定时任务） */
    private Boolean enabled;

    /** 超时时间（秒），0 表示不限制 */
    @TableField(value = "timeout_seconds")
    private Integer timeoutSeconds;

    /** 指定 Worker ID（为空表示自动调度） */
    @TableField(value = "target_worker")
    private String targetWorker;

    /** 任务状态：active / paused / deleted */
    private String status;

    /** 创建人 */
    @TableField(value = "created_by")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

