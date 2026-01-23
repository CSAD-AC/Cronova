package com.sjdt.scheduler.domain.dto;

import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

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

    /** 是否启用 */
    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    /** 超时时间（秒），0表示不限制 */
    @Schema(description = "超时时间（秒），0表示不限制", example = "60")
    private Integer timeoutSeconds;

    /** 指定Worker ID（为空表示自动调度，仅script类型有效） */
    @Schema(description = "指定Worker ID（为空表示自动调度，仅script类型有效）", example = "worker-001")
    private String targetWorker;

    /** 任务状态 */
    @Schema(description = "任务状态", example = "active")
    private TaskStatus status;

    /** 回调配置（callback类型可更新） */
    @Schema(description = "回调配置（callback类型可更新）")
    private CallbackJobConfigUpdateDTO callbackConfig;
}
