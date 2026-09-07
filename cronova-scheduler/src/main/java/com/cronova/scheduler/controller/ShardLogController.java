package com.cronova.scheduler.controller;

import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.vo.ShardBatchInfo;
import com.cronova.scheduler.domain.vo.ShardLogResult;
import com.cronova.scheduler.service.ShardLogAggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 分片批次日志聚合查看接口（运行中游标归并）。
 */
@RestController
@RequestMapping("/api/shard-batches")
@Tag(name = "分片批次日志API", description = "分片任务日志按批次聚合查看（按时间排序）")
@Slf4j
public class ShardLogController {

    private final ShardLogAggregateService shardLogAggregateService;

    @Autowired
    public ShardLogController(ShardLogAggregateService shardLogAggregateService) {
        this.shardLogAggregateService = shardLogAggregateService;
    }

    /** 运行中游标归并日志（无状态游标协议，按时间稳定排序） */
    @GetMapping("/{batchId}/logs")
    @Operation(summary = "分片批次聚合日志", description = "按游标增量归并各片日志，按时间稳定排序")
    public Result<ShardLogResult> getShardLogs(
            @Parameter(description = "分片批次ID", required = true) @PathVariable String batchId,
            @Parameter(description = "不透明游标（首次为空，后续原样带回）") @RequestParam(defaultValue = "") String cursor,
            @Parameter(description = "每片读取字节上限，默认64KB，最大1MB") @RequestParam(defaultValue = "65536") int limit,
            @Parameter(description = "按级别过滤（如 ERROR）") @RequestParam(required = false) String level,
            @Parameter(description = "按分片序号过滤") @RequestParam(required = false) Integer shard) {
        log.info("收到分片批次聚合日志请求，batchId: {}, level: {}, shard: {}", batchId, level, shard);
        if (!shardLogAggregateService.batchExists(batchId)) {
            return Result.error(404, "Shard batch not found");
        }
        return Result.success(shardLogAggregateService.aggregate(batchId, cursor, limit, level, shard));
    }

    /** 分片批次信息（片列表 + 状态 + 是否终态） */
    @GetMapping("/{batchId}/logs/info")
    @Operation(summary = "分片批次信息", description = "分片任务批次内各片的最新状态")
    public Result<ShardBatchInfo> getShardInfo(
            @Parameter(description = "分片批次ID", required = true) @PathVariable String batchId) {
        log.info("收到分片批次信息请求，batchId: {}", batchId);
        ShardBatchInfo info = shardLogAggregateService.batchInfo(batchId);
        if (info.getShards().isEmpty()) {
            return Result.error(404, "Shard batch not found");
        }
        return Result.success(info);
    }
}
