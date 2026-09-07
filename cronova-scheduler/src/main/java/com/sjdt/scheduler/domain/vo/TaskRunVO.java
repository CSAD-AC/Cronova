package com.sjdt.scheduler.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行记录视图对象（用于仪表盘展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRunVO {

    /** 执行记录ID */
    private String id;

    /** 任务名称（关联的任务名称） */
    private String taskName;

    /** 执行类型：script(脚本执行) / callback(HTTP回调) */
    private String executeType;

    /** 脚本类型（script类型有值） */
    private String scriptType;

    /** 执行状态：pending / running / success / failed / timeout */
    private String status;

    /** 退出码 */
    private Integer exitCode;

    /** 执行耗时（毫秒） */
    private Long duration;

    /** 开始时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /** 创建时间（执行记录生成时间） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /** 错误信息（失败时可能有值） */
    private String errorMessage;
}
