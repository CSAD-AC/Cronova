package com.cronova.scheduler.domain.message;

import lombok.Data;

/**
 * Worker 状态报告请求 DTO
 */
@Data
public class WorkerStatusDTO {

    /** CPU 使用率（百分比） */
    private Double cpuUsage;

    /** 内存使用率（百分比） */
    private Double memUsage;

    /** 当前运行的任务数 */
    private Integer runningTasks;

}