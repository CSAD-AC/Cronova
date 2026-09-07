package com.sjdt.scheduler.domain.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务结果消息
 * 用于Worker向调度器返回任务执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResultMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 执行器ID */
    private String workerId;

    /** 任务ID */
    private String taskId;

    /** 任务运行实例ID */
    private String taskRunId;

    /** 任务运行状态: pending/running/success/failed/timeout */
    private String status;

    /** 进程退出码（如有） */
    private Integer exitCode;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 失败原因（如有） */
    private String errorMessage;
    
    /** 日志文件MinIO路径 */
    private String logPath;

    /** 分片身份（日志聚合用；非分片任务为 null） */
    private Integer shardIndex;
    private Integer shardTotal;
    private String shardBatchId;
}