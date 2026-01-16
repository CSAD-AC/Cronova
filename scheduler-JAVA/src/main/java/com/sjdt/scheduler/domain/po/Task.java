package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务定义实体
 * 支持脚本执行和HTTP回调任务
 * 脚本执行：type 表示脚本语言，callbackConfigId 为 NULL
 * HTTP回调：callbackConfigId 非 NULL，type 可为 NULL
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

    /** 任务描述 */
    private String description;

    /** 脚本类型：java/python/go（脚本执行任务必填，HTTP回调任务可为NULL） */
    private ScriptType type;

    /** 脚本id（脚本执行类型必填） */
    @TableField(value = "script_id")
    private String scriptId;

    /** 回调配置id（非NULL表示HTTP回调任务） */
    @TableField(value = "callback_config_id")
    private String callbackConfigId;

    /** 任务调度类型：once / cron */
    @TableField(value = "task_type")
    private TaskType taskType;

    /**
     * Cron 表达式
     * 示例：0 0 * * *
     * 当 taskType = cron 时必须存在
     */
    @TableField(value = "cron_expr")
    private String cronExpr;

    /** 是否启用 */
    private Boolean enabled;

    /** 超时时间（秒），0 表示不限制 */
    @TableField(value = "timeout_seconds")
    private Integer timeoutSeconds;

    /** 指定 Worker ID（为空表示自动调度） */
    @TableField(value = "target_worker")
    private String targetWorker;

    /** 任务状态：active / paused / deleted */
    private TaskStatus status;

    /** 创建人 */
    @TableField(value = "created_by")
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

