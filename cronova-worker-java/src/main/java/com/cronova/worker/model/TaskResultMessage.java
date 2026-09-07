package com.cronova.worker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务结果消息（发送到 TASK_RESULT 主题 / 上报 /api/worker/task-status）
 * <p>
 * 时间字段统一输出 ISO_LOCAL_DATE_TIME（无 Z、无毫秒，如 2025-05-20T15:30:00），
 * 与调度器 TaskResultMessage(LocalDateTime) 的 Jackson 默认序列化互操作；
 * 运行中上报时 endTime 置 null 并被忽略（调度器不接受空字符串日期）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskResultMessage {

    private String workerId;
    private String taskId;
    private String taskRunId;

    /** pending / running / success / failed / timeout */
    private String status;

    private Integer exitCode;
    private String startTime;
    private String endTime;
    private Long durationMs;
    private String errorMessage;
    private String logPath;

    /** 分片身份（日志聚合用；非分片任务为 null） */
    private Integer shardIndex;
    private Integer shardTotal;
    private String shardBatchId;
}
