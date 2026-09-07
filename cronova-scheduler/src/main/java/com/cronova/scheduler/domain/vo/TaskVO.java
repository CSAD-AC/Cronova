package com.cronova.scheduler.domain.vo;

import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.ScriptType;
import com.cronova.scheduler.domain.enums.TaskStatus;
import com.cronova.scheduler.domain.enums.TaskType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 任务视图对象（返回给前端）
 */
@Data
public class TaskVO {

    /** 任务ID */
    private String id;

    /** 任务名称 */
    private String name;

    /** 任务描述 */
    private String description;

    /** 执行类型：script(脚本执行) / callback(HTTP回调) */
    private ExecuteType executeType;

    /** 脚本类型 */
    private ScriptType scriptType;

    /** 脚本ID */
    private String scriptId;

    /** 任务类型 */
    private TaskType taskType;

    /** Cron表达式 */
    private String cronExpr;



    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** 重试次数 */
    private Integer retryCount;

    /** 重试间隔（秒） */
    private Integer retryIntervalSeconds;

    /** 指定Worker ID */
    private String targetWorker;

    /** 路由策略 */
    private String routeStrategy;

    /** 阻塞策略 */
    private String blockStrategy;

    /** 分片总数 */
    private Integer shardTotal;

    /** 任务需要的标签（用于自动匹配Worker） */
    private Set<String> requiredTags;

    /** 任务状态 */
    private TaskStatus status;

    /** 执行耗时（毫秒），用于任务执行记录 */
    private Long duration;

    /** 任务实例执行状态（pending/running/success/failed/timeout），用于仪表盘展示 */
    private String runStatus;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 回调配置信息（callback类型有值） */
    private CallbackJobConfigVO callbackConfig;
}
