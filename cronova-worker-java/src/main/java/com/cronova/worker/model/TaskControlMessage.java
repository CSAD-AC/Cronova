package com.cronova.worker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务控制消息（对应 TASK_CONTROL 主题，Tag: STOP_TASK / PAUSE_TASK / RESUME_TASK /
 * DELETE_TASK / KILL_TASK / UPDATE_TAGS）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskControlMessage {

    private String taskId;
    private String taskRunId;
    private String targetWorkerId;
    private String reason;
    private String timestamp;

    /** UPDATE_TAGS 消息体 */
    private String workerId;
    private java.util.List<String> tags;
}
