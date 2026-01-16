package com.example.controller;

import com.sjdt.scheduler.controller.ScheduledJobBase;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 数据同步回调控制器
 * 继承 ScheduledJobBase，提供统一的回调入口
 */
@Slf4j
@RestController
@RequestMapping("/api/callback/data-sync")
@AllArgsConstructor
public class DataSyncCallback extends ScheduledJobBase {

    private final DataSyncService dataSyncService;

    /**
     * 统一回调入口
     * 调度器通过此接口触发数据同步任务
     */
    @PostMapping("/job")
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param, HttpServletRequest request) {
        return super.handle(request, param);
    }

    /**
     * 返回密钥，由调度器配置匹配
     */
    @Override
    protected String secretKey() {
        return "data-sync-secret-key-2026";
    }

    /**
     * 实现业务逻辑
     */
    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        String syncType = (String) param.get("syncType");
        String date = (String) param.get("date");

        log.info("开始执行数据同步任务, syncType={}, date={}", syncType, date);

        // 调用业务服务
        SyncResult result = dataSyncService.sync(syncType, date);

        log.info("数据同步完成, 处理记录数={}, 耗时={}ms", result.getRecordsProcessed(), result.getDurationMs());

        return Result.ok(result);
    }

    /**
     * 数据同步服务
     */
    @Data
    public static class SyncResult {
        private int recordsProcessed;
        private long durationMs;
        private String status;
        private String message;
    }
}
