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
 * 任务日志实体
 * 仅保存 MinIO 路径，不保存日志内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_log")
public class TaskLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联的运行实例 ID */
    private String runId;

    /** stdout 日志在 MinIO 中的路径 */
    private String stdoutPath;

    /** stderr 日志在 MinIO 中的路径 */
    private String stderrPath;

    /** 分片批次ID（同一 shardBatchId 的所有分片共享；非分片任务也可为 N=1 批次） */
    private String shardBatchId;

    /** 分片序号（0 起；非分片任务为 0） */
    private Integer shardIndex;

    /** 分片总数（非分片任务为 1） */
    private Integer shardTotal;

    /** 创建时间 */
    private LocalDateTime createTime;

}

