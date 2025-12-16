package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
/**
 * Worker 执行器节点实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("workers")
public class Worker implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
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

}
