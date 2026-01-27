package com.sjdt.scheduler.domain.vo;

import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import lombok.Data;

import java.time.LocalDateTime;

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

    /** 是否启用 */
    private Boolean enabled;

    /** 超时时间（秒） */
    private Integer timeoutSeconds;

    /** 重试次数 */
    private Integer retryCount;

    /** 重试间隔（秒） */
    private Integer retryIntervalSeconds;

    /** 指定Worker ID */
    private String targetWorker;

    /** 任务状态 */
    private TaskStatus status;

    /** 创建人 */
    private String createdBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 回调配置信息（callback类型有值） */
    private CallbackJobConfigVO callbackConfig;
}
