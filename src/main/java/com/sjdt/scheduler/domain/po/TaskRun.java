package com.sjdt.scheduler.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 任务执行实例
 * 表示 Task 的一次实际运行
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("task_run")
public class TaskRun implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private String id;

    /** 关联的任务 ID（task.taskId） */
    private String taskId;

    /** 实际执行该任务的 Worker ID */
    private String workerId;

    /**
     * 执行状态：
     * pending / running / success / failed / timeout
     */
    private TaskRunStatus status;

    /** 进程退出码 */
    private Integer exitCode;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（毫秒） */
    private Long durationMs;

    /** 失败或异常原因 */
    private String errorMessage;

    /** 创建时间（实例生成时间） */
    private LocalDateTime createTime;

}

