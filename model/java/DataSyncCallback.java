package com.example.controller;

import com.sjdt.scheduler.controller.ScheduledJobBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 数据同步回调控制器
 *
 * 【接口说明】
 * 1. 回调接口：POST /api/callback/data-sync
 * 2. 健康接口：GET  /api/callback/data-sync/health
 *
 * 【健康检查接口约定】
 * - 路径格式：<回调接口路径>/health（强制约定）
 * - 当前：/api/callback/data-sync/health
 * - 功能：仅验证 X-Secret 和 X-Request-Id 请求头
 * - 要求：健康接口和回调接口必须使用相同的 BaseUrl
 */
@Slf4j
@RestController
@RequestMapping("/api/callback/data-sync")
public class DataSyncCallback extends ScheduledJobBase {

    /**
     * 回调接口
     * 接收调度器的回调请求
     */
    @PostMapping
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param,
                           HttpServletRequest request) {
        return super.handle(request, param);
    }

    /**
     * 健康检查接口
     *
     * 【重要】接口路径约定
     * - 路径：<回调接口基础路径>/health
     * - 当前：/api/callback/data-sync/health
     *
     * 功能：仅验证请求头（X-Secret 和 X-Request-Id），不执行业务逻辑
     */
    @GetMapping("/health")
    public Result<?> healthCheck(HttpServletRequest request) {
        return super.healthCheck(request);
    }

    /**
     * 返回密钥
     * 【注意】此密钥需要与调度器回调配置中的密钥保持一致
     */
    @Override
    protected String secretKey() {
        return "your-secret-key-here";
    }

    /**
     * 返回模块名称
     * 用于健康检查响应的 endpoint 字段
     */
    @Override
    protected String endpoint() {
        return "数据同步服务";
    }

    /**
     * 执行业务逻辑
     */
    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        String requestId = param.containsKey("_request_id") ?
            param.get("_request_id").toString() : "unknown";

        // 【规范】日志开头带上 [requestId]
        log.info("[{}] 开始执行数据同步任务，参数: {}", requestId, param);

        try {
            // 执行业务逻辑
            Map<String, Object> result = executeSyncLogic(param);

            log.info("[{}] 数据同步任务执行成功，处理记录数: {}",
                     requestId, result.get("recordsProcessed"));

            return Result.ok(result);

        } catch (Exception e) {
            log.error("[{}] 数据同步任务执行失败: {}", requestId, e.getMessage(), e);
            return Result.fail(500, "执行失败: " + e.getMessage());
        }
    }

    private Map<String, Object> executeSyncLogic(Map<String, Object> param) {
        // 模拟业务逻辑
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("recordsProcessed", 100);
        result.put("durationMs", 1500);
        result.put("status", "completed");
        return result;
    }
}
