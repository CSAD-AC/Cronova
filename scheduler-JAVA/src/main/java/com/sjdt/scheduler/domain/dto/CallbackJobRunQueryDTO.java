package com.sjdt.scheduler.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回调执行记录查询条件DTO
 * 用于分页查询回调执行记录
 */
@Data
@Schema(description = "回调执行记录查询条件")
public class CallbackJobRunQueryDTO {

    /** 任务ID（精确匹配） */
    @Schema(description = "任务ID", example = "task-001")
    private String taskId;

    /** 配置ID（精确匹配） */
    @Schema(description = "配置ID", example = "config-001")
    private String configId;

    /** 任务运行实例ID（精确匹配） */
    @Schema(description = "任务运行实例ID", example = "run-001")
    private String taskRunId;

    /** 请求ID（精确匹配） */
    @Schema(description = "请求ID", example = "abc123def456")
    private String requestId;

    /** 执行状态（精确匹配） */
    @Schema(description = "执行状态", example = "success", allowableValues = {"pending", "running", "success", "failed", "timeout"})
    private String status;

    /** 响应状态码范围（最小值） */
    @Schema(description = "响应状态码最小值", example = "200")
    private Integer statusCodeMin;

    /** 响应状态码范围（最大值） */
    @Schema(description = "响应状态码最大值", example = "599")
    private Integer statusCodeMax;

    /** 开始时间范围（起始） */
    @Schema(description = "开始时间范围起始", example = "2024-01-01T00:00:00")
    private LocalDateTime startTimeFrom;

    /** 开始时间范围（结束） */
    @Schema(description = "开始时间范围结束", example = "2024-12-31T23:59:59")
    private LocalDateTime startTimeTo;

    /** 创建时间范围（起始） */
    @Schema(description = "创建时间范围起始", example = "2024-01-01T00:00:00")
    private LocalDateTime createTimeFrom;

    /** 创建时间范围（结束） */
    @Schema(description = "创建时间范围结束", example = "2024-12-31T23:59:59")
    private LocalDateTime createTimeTo;

    /** 执行耗时范围（最小值，毫秒） */
    @Schema(description = "执行耗时最小值（毫秒）", example = "0")
    private Integer durationMin;

    /** 执行耗时范围（最大值，毫秒） */
    @Schema(description = "执行耗时最大值（毫秒）", example = "60000")
    private Integer durationMax;

    /** 是否只查询失败记录 */
    @Schema(description = "是否只查询失败记录", example = "false")
    private Boolean onlyFailed;

    /** 页码（从1开始） */
    @Schema(description = "页码，从1开始", example = "1")
    private Integer pageNum;

    /** 每页大小 */
    @Schema(description = "每页大小", example = "20")
    private Integer pageSize;

    /** 排序字段 */
    @Schema(description = "排序字段", example = "createTime")
    private String orderBy;

    /** 排序方向：asc/desc */
    @Schema(description = "排序方向", example = "desc", allowableValues = {"asc", "desc"})
    private String orderDirection;

    /**
     * 获取分页参数默认值
     */
    public Integer getPageNum() {
        return pageNum != null && pageNum > 0 ? pageNum : 1;
    }

    public Integer getPageSize() {
        return pageSize != null && pageSize > 0 ? pageSize : 20;
    }

    public String getOrderBy() {
        return orderBy != null && !orderBy.isEmpty() ? orderBy : "createTime";
    }

    public String getOrderDirection() {
        return orderDirection != null && (orderDirection.equalsIgnoreCase("asc") || orderDirection.equalsIgnoreCase("desc"))
            ? orderDirection.toLowerCase() : "desc";
    }
}
