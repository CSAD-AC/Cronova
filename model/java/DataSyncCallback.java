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

    @PostMapping("/job")
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param, HttpServletRequest request) {
        return super.handle(request, param);
    }

    @Override
    protected String secretKey() {
        return "data-sync-secret-2026";
    }

    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        log.info("开始数据同步: syncType={}", param.get("syncType"));
        DataSyncResult result = dataSyncService.sync(param);
        log.info("数据同步完成: records={}", result.getRecordsProcessed());
        return Result.ok(result);
    }

    @Data
    public static class DataSyncResult {
        private int recordsProcessed;
        private long durationMs;
        private String status;
    }
}
