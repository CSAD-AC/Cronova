package com.cronova.scheduler.domain.dto;

import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.ScriptType;
import com.cronova.scheduler.domain.enums.TaskStatus;
import com.cronova.scheduler.domain.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Set;

/**
 * 任务更新请求DTO
 */
@Data
@Schema(description = "任务更新请求DTO")
public class TaskUpdateDTO {

    /** 任务ID */
    @Schema(description = "任务ID", example = "12345")
    @NotBlank(message = "任务ID不能为空")
    private String id;

    /** 任务名称 */
    @Schema(description = "任务名称", example = "数据同步任务")
    @NotBlank(message = "任务名称不能为空")
    private String name;

    /** 任务描述 */
    @Schema(description = "任务描述", example = "这是一个定时数据同步任务")
    private String description;

    /** 执行类型：script(脚本执行) / callback(HTTP回调) */
    @Schema(description = "执行类型：script(脚本执行) / callback(HTTP回调)", example = "callback")
    @NotNull(message = "执行类型不能为空")
    private ExecuteType executeType;

    /** 脚本类型（script类型必填） */
    @Schema(description = "脚本类型：java/python/go（script类型必填）", example = "python")
    private ScriptType scriptType;

    /** 脚本ID（script类型必填） */
    @Schema(description = "脚本ID（script类型必填）", example = "b73f2e86fad8dfaf74d23c005ee2328e")
    private String scriptId;

    /** 任务类型 */
    @Schema(description = "任务类型：once(一次性) / cron(定时)", example = "cron")
    @NotNull(message = "任务类型不能为空")
    private TaskType taskType;

    /** Cron表达式（仅cron任务使用） */
    @Schema(description = "Cron表达式，仅在定时任务时使用", example = "0 0 12 * * ?")
    @Pattern(
            regexp = "^$|^([0-9*?\\-,/]+(\\s+[0-9*?\\-,/]+){5})$",
            message = "Cron表达式格式不正确"
    )
    private String cronExpr;



    /** 超时时间（秒），0表示不限制 */
    @Schema(description = "超时时间（秒），0表示不限制", example = "60")
    private Integer timeoutSeconds;

    /** 重试次数 */
    @Schema(description = "重试次数", example = "3")
    private Integer retryCount;

    /** 重试间隔（秒） */
    @Schema(description = "重试间隔（秒）", example = "60")
    private Integer retryIntervalSeconds;

    /** 指定Worker ID（为空表示自动调度，仅script类型有效） */
    @Schema(description = "指定Worker ID（为空表示自动调度，仅script类型有效）", example = "worker-001")
    private String targetWorker;

    /** 路由策略：ROUND_ROBIN / RANDOM / CONSISTENT_HASH / LEAST_LOAD / FAILOVER / TAG_MATCH */
    @Schema(description = "路由策略：ROUND_ROBIN / RANDOM / CONSISTENT_HASH / LEAST_LOAD / FAILOVER / TAG_MATCH", example = "LEAST_LOAD")
    private String routeStrategy;

    /** 阻塞策略（路由层保留，调度层面执行）：SERIAL / DISCARD_LATER / COVER_EARLY */
    @Schema(description = "阻塞策略：SERIAL / DISCARD_LATER / COVER_EARLY", example = "DISCARD_LATER")
    private String blockStrategy;

    /** 分片总数（>1 时任务分片并发投递） */
    @Schema(description = "分片总数（>1 时任务分片并发投递）", example = "1")
    private Integer shardTotal;

    /** 任务需要的标签（用于自动匹配Worker） */
    @Schema(description = "任务需要的标签（用于自动匹配Worker），如: [\"V1\", \"FastAPI\"]")
    private Set<String> requiredTags;

    /** 任务状态 */
    @Schema(description = "任务状态", example = "active")
    private TaskStatus status;

    /** 回调配置（callback类型可更新） */
    @Schema(description = "回调配置（callback类型可更新）")
    private CallbackJobConfigUpdateDTO callbackConfig;
}
