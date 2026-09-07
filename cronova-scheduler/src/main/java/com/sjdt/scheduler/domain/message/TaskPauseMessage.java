package com.sjdt.scheduler.domain.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务暂停消息
 * 用于调度器向执行器发送暂停任务实例的指令
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPauseMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 任务运行实例ID */
    private String taskRunId;

    /** 目标执行器ID */
    private String targetWorkerId;

    /** 暂停原因 */
    private String reason;

    /** 暂停时间戳 */
    private String timestamp;
}
