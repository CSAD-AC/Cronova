package com.api.auth.controller.callback;

import com.api.auth.common.Result;
import com.api.auth.service.IStaffSyncService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 同步H4A员工信息回调控制器
 *
 * 【接口说明】
 * 1. 回调接口：POST /api/callback/sync-h4a-staff
 * 2. 健康接口：GET  /api/callback/sync-h4a-staff/health
 *
 * 【配置示例】
 * 在配置文件中添加：
 * callback:
 *   sync-h4a-staff:
 *     secret-key: your-secret-key-here
 *     enabled: true
 *
 * 【健康检查接口约定】
 * - 路径：<回调接口基础路径>/health
 * - 当前：/api/callback/sync-h4a-staff/health
 * - 功能：仅验证 X-Secret 和 X-Request-Id 请求头，不执行业务逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/callback/sync-h4a-staff")
public class SyncH4AStaffCallback extends ScheduledJobBase {

    private final IStaffSyncService staffSyncService;

    @Value("${callback.sync-h4a-staff.secret-key:sync-h4a-staff-secret-key-2026}")
    private String secretKey;

    @Value("${callback.sync-h4a-staff.enabled:false}")
    private boolean enabled;

    public SyncH4AStaffCallback(IStaffSyncService staffSyncService) {
        this.staffSyncService = staffSyncService;
        log.info("✅ SyncH4AStaffCallback 初始化完成，启用状态: {}", enabled);
    }

    /**
     * 回调接口
     * 接收调度器的回调请求，执行同步H4A员工信息到ES的任务
     *
     * @param param 请求参数（可选）
     * @param request HTTP请求
     * @return 执行结果
     */
    @PostMapping
    public Result<?> handle(@RequestBody(required = false) Map<String, Object> param,
                           HttpServletRequest request) {
        if (!enabled) {
            log.warn("回调接口已被禁用，请先在配置中启用");
            return Result.error(403, "回调接口已禁用");
        }
        return super.handle(request, param);
    }

    /**
     * 健康检查接口
     *
     * 【重要】接口路径约定
     * - 路径：<回调接口基础路径>/health
     * - 当前：/api/callback/sync-h4a-staff/health
     *
     * 功能：仅验证请求头（X-Secret 和 X-Request-Id），不执行业务逻辑
     *
     * @param request HTTP请求
     * @return 健康检查结果
     */
    @GetMapping("/health")
    public Result<?> healthCheck(HttpServletRequest request) {
        if (!enabled) {
            log.warn("健康检查接口已被禁用，请先在配置中启用");
            return Result.error(403, "回调接口已禁用");
        }
        return super.healthCheck(request);
    }

    /**
     * 返回密钥
     * 【注意】此密钥需要与调度器回调配置中的密钥保持一致
     *
     * @return 密钥字符串
     */
    @Override
    protected String secretKey() {
        return secretKey;
    }

    /**
     * 返回模块名称
     * 用于健康检查响应的 endpoint 字段
     *
     * @return 模块名称
     */
    @Override
    protected String endpoint() {
        return "同步H4A员工信息服务";
    }

    /**
     * 执行业务逻辑
     * 执行同步H4A员工信息到Elasticsearch的任务
     *
     * @param param 请求参数（可选）
     * @return 执行结果
     */
    @Override
    protected Result<?> runJob(Map<String, Object> param) throws Exception {
        String requestId = getRequestId(param);
        long startTime = System.currentTimeMillis();

        log.info("[{}] 开始执行【同步H4A员工信息到ES】任务，触发类型: {}", requestId, getTriggerType(param));
        log.info("[{}] 任务参数: {}", requestId, param);

        try {
            boolean success = staffSyncService.syncH4AStaffToElasticsearch();

            long duration = System.currentTimeMillis() - startTime;
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("success", success);
            resultData.put("durationMs", duration);
            resultData.put("triggerType", getTriggerType(param));

            if (success) {
                log.info("[{}] 【同步H4A员工信息到ES】任务执行成功！耗时: {}ms", requestId, duration);
                return Result.success(resultData);
            } else {
                log.warn("[{}] 【同步H4A员工信息到ES】任务执行失败或未同步数据！耗时: {}ms", requestId, duration);
                return Result.error(500, "任务执行失败或未同步数据");
            }

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 【同步H4A员工信息到ES】任务执行异常！耗时: {}ms", requestId, duration, e);
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            errorData.put("phase", "sync");
            errorData.put("durationMs", duration);
            return Result.error(500, "执行失败: " + e.getMessage());
        }
    }

    /**
     * 从请求参数中获取 RequestId
     * 优先从参数中获取，其次使用默认值
     *
     * @param param 请求参数
     * @return RequestId
     */
    private String getRequestId(Map<String, Object> param) {
        if (param != null && param.containsKey("_request_id")) {
            return param.get("_request_id").toString();
        }
        return "callback-" + System.currentTimeMillis();
    }

    /**
     * 从请求参数中获取触发类型
     *
     * @param param 请求参数
     * @return 触发类型
     */
    private String getTriggerType(Map<String, Object> param) {
        if (param != null && param.containsKey("trigger_type")) {
            return param.get("trigger_type").toString();
        }
        return "回调触发";
    }
}
