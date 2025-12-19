package com.sjdt.scheduler.domain.dto;

import lombok.Data;

/**
 * Worker 注册请求 DTO
 */
@Data
public class WorkerRegisterDTO {

    /** 主机名 */
    private String host;

    /** 内网 IP 地址 */
    private String ip;

    /** CPU 使用率（百分比） */
    private Double cpuUsage;

    /** 内存使用率（百分比） */
    private Double memUsage;

    /** 当前运行的任务数 */
    private Integer runningTasks;

}