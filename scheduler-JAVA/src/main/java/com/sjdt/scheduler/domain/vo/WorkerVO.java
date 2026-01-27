package com.sjdt.scheduler.domain.vo;

import com.sjdt.scheduler.domain.po.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Worker 详细信息 VO
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WorkerVO {

    /** 主键 */
    private String id;

    /** 主机名 */
    private String host;

    /** 内网 IP 地址 */
    private String ip;

    /** Worker 状态：online / offline / busy */
    private String status;

    /** CPU 使用率（百分比） */
    private Double cpuUsage;

    /** 内存使用率（百分比） */
    private Double memUsage;

    /** 当前运行的任务数 */
    private Integer runningTasks;

    /** 最近一次心跳时间 */
    private LocalDateTime lastHeartbeat;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 环境标签 */
    private Set<Tag> tags;

}