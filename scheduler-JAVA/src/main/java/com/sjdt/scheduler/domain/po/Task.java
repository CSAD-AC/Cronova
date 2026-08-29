package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.sjdt.scheduler.domain.enums.ExecuteType;
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
 * 脚本执行：executeType = script，需要 scriptType 和 scriptId
 * HTTP回调：executeType = callback，通过 callback_job_configs 表关联
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

    /** 执行类型：script(脚本执行) / callback(HTTP回调) */
    @TableField(value = "execute_type")
    private ExecuteType executeType;

    /** 脚本类型：java/python/go（script类型必填） */
    @TableField(value = "script_type")
    private ScriptType scriptType;

    /** 脚本id（脚本执行类型必填） */
    @TableField(value = "script_id")
    private String scriptId;

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



    /** 超时时间（秒），0 表示不限制 */
    @TableField(value = "timeout_seconds")
    private Integer timeoutSeconds;

    /** 重试次数 */
    @TableField(value = "retry_count")
    private Integer retryCount;

    /** 重试间隔（秒） */
    @TableField(value = "retry_interval_seconds")
    private Integer retryIntervalSeconds;

    /** 指定 Worker ID（为空表示自动调度，仅script类型有效） */
    @TableField(value = "target_worker")
    private String targetWorker;

    /** 任务需要的标签（JSON格式，如: ["V1","FastAPI"]），用于自动匹配Worker */
    @TableField(value = "required_tags")
    private String requiredTags;

    /** 任务状态：active / paused / deleted */
    @TableField(value = "status")
    private TaskStatus status;

    /** 创建人 */
    @TableField(value = "created_by")
    private String createdBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "UTC")
    private LocalDateTime updateTime;

    /** 下次理论触发时间（驱动到期扫描，CAS 抢占依据） */
    @TableField(value = "next_trigger_time")
    private LocalDateTime nextTriggerTime;

    /** 上次实际触发时间 */
    @TableField(value = "last_trigger_time")
    private LocalDateTime lastTriggerTime;

    /** 路由策略：ROUND_ROBIN/RANDOM/CONSISTENT_HASH/LEAST_LOAD/FAILOVER/TAG_MATCH/SHARD */
    @TableField(value = "route_strategy")
    private String routeStrategy;

    /** 阻塞策略：SERIAL/DISCARD_LATER/COVER_EARLY */
    @TableField(value = "block_strategy")
    private String blockStrategy;

    /** 分片总数（NULL/0=不分片） */
    @TableField(value = "shard_total")
    private Integer shardTotal;
}