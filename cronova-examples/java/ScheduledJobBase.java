package com.api.auth.controller.callback;

import com.api.auth.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时任务回调基类
 *
 * 【使用说明】
 *
 * 一、核心信息
 *   - 请求方法：POST
 *   - 请求头：X-Secret（密钥）、X-Request-Id（请求ID）
 *   - 请求体：可选，不做强制要求
 *
 * 二、子类必须实现的方法
 *   1. secretKey()     - 返回密钥，用于 X-Secret 校验
 *   2. endpoint()      - 返回模块名称，用于健康检查响应
 *   3. runJob(param)   - 执行业务逻辑，返回 Result
 *
 * 三、子类需开放的接口
 *   - 提供 POST 接口接收回调请求，内部调用 super.handle()
 *   - 提供 GET 接口用于健康检查，路径为回调接口 + "/health"
 *
 * 四、接口约定
 *   【重要】健康检查接口
 *   - 路径格式：<回调接口路径>/health（强制约定）
 *   - 示例：
 *     · 回调接口：/api/callback/delete-junk-file
 *     · 健康接口：/api/callback/delete-junk-file/health
 *   - 功能：仅验证 X-Secret 和 X-RequestId 请求头
 *   - 要求：健康接口和回调接口必须使用相同的 BaseUrl
 *
 * 五、日志规范
 *   - 务必在日志中输出 requestId，便于快速定位日志
 *   - 示例：log.info("[{}] 开始执行任务", requestId)
 *
 * 【请求头示例】
 *   X-Request-Id: abc123def456
 *   X-Secret: your-secret-key
 *
 */
@Slf4j
public abstract class ScheduledJobBase {

    private static final ThreadLocal<ObjectMapper> MAPPER_LOCAL = ThreadLocal.withInitial(ObjectMapper::new);

    private static final String DEFAULT_SECRET_KEY = "default-secret-key-placeholder";
    private static final String DEFAULT_ENDPOINT = "回调服务";
    private static final String PLACEHOLDER_CLASS_NAME = "ScheduledJobBase";

    /**
     * 返回密钥，用于 X-Secret 校验
     * 子类必须重写此方法
     */
    protected abstract String secretKey();

    /**
     * 返回模块名称，用于健康检查响应的 endpoint 字段
     * 子类应重写此方法，返回有意义的模块名称
     *
     * @return 模块名称，如"数据同步服务"、"报表生成服务"等
     */
    protected String endpoint() {
        return DEFAULT_ENDPOINT;
    }

    /**
     * 执行业务逻辑
     * 子类必须重写此方法实现具体业务
     *
     * @param param 请求参数（可选）
     * @return 执行结果
     */
    protected abstract Result<?> runJob(Map<String, Object> param) throws Exception;

    /**
     * 统一处理入口
     *
     * 子类 Controller 使用示例：
     * @PostMapping
     * public Result<?> handle(@RequestBody(required = false) Map<String, Object> param, HttpServletRequest request) {
     *     return super.handle(request, param);
     * }
     */
    public Result<?> handle(HttpServletRequest request, @RequestBody(required = false) Map<String, Object> param) {
        long start = System.currentTimeMillis();
        String requestId = request.getHeader("X-Request-Id");

        if (requestId == null || requestId.isEmpty()) {
            return Result.error(400, "缺少请求头 X-Request-Id");
        }

        try {
            validateMethodOverrides(requestId);
            validateSecret(request);
            Result<?> result = runJob(param == null ? Map.of() : param);
            if (result == null) {
                result = Result.success(null);
            }
            log.info("[{}] 任务执行完成，耗时: {}ms", requestId, System.currentTimeMillis() - start);
            return result;
        } catch (SecurityException e) {
            log.warn("[{}] 密钥验证失败: {}", requestId, e.getMessage());
            return Result.error(401, "密钥验证失败");
        } catch (IllegalStateException e) {
            log.error("[{}] 方法重写校验失败: {}", requestId, e.getMessage());
            return Result.error(500, "配置错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("[{}] 任务执行出错", requestId, e);
            return Result.error(500, "执行失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查入口
     *
     * 【接口约定】
     * - 路径格式：<回调接口路径>/health（强制约定）
     * - 功能：仅验证 X-Secret 和 X-RequestId 请求头，不执行业务逻辑
     * - 要求：健康接口和回调接口必须使用相同的 BaseUrl
     *
     * 【响应格式】
     * {
     *   "code": 200,
     *   "message": "操作成功",
     *   "data": {
     *     "status": "UP",
     *     "timestamp": "2024-01-19T10:30:00",
     *     "endpoint": "数据同步服务",
     *     "message": "服务正常"
     *   }
     * }
     *
     * 子类 Controller 使用示例：
     * @GetMapping("/health")
     * public Result<?> healthCheck(HttpServletRequest request) {
     *     return super.healthCheck(request);
     * }
     *
     * @param request HTTP请求
     * @return 健康检查结果
     */
    public Result<?> healthCheck(HttpServletRequest request) {
        long start = System.currentTimeMillis();
        String requestId = request.getHeader("X-Request-Id");
        String requestPath = request.getRequestURI();
        String remoteAddr = getRemoteAddr(request);

        log.info("[{}] 收到健康检查请求，路径: {}，来源: {}", requestId, requestPath, remoteAddr);

        if (requestId == null || requestId.isEmpty()) {
            log.warn("[{}] 健康检查失败: 缺少请求头 X-Request-Id，路径: {}", requestId, requestPath);
            return Result.error(400, "缺少请求头 X-Request-Id");
        }

        try {
            String secret = request.getHeader("X-Secret");
            validateSecret(request);

            Map<String, Object> healthData = new HashMap<>();
            healthData.put("status", "UP");
            healthData.put("timestamp", LocalDateTime.now().toString());
            healthData.put("endpoint", this.endpoint());
            healthData.put("message", "服务正常");

            long cost = System.currentTimeMillis() - start;
            log.info("[{}] 健康检查通过，endpoint: {}，耗时: {}ms", requestId, this.endpoint(), cost);

            return Result.success(healthData);

        } catch (SecurityException e) {
            log.warn("[{}] 健康检查失败: 密钥验证失败，路径: {}，来源: {}", requestId, requestPath, remoteAddr);
            return Result.error(401, "密钥验证失败");
        } catch (Exception e) {
            log.error("[{}] 健康检查异常: {}，路径: {}", requestId, e.getMessage(), requestPath, e);
            return Result.error(500, "健康检查异常: " + e.getMessage());
        }
    }

    /**
     * 获取请求来源IP地址
     *
     * @param request HTTP请求
     * @return IP地址
     */
    private String getRemoteAddr(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 防御性校验：验证子类是否正确重写了抽象方法
     *
     * @param requestId 请求ID，用于日志输出
     * @throws IllegalStateException 如果方法未正确重写
     */
    private void validateMethodOverrides(String requestId) {
        String className = this.getClass().getSimpleName();

        if (className.equals(PLACEHOLDER_CLASS_NAME)) {
            throw new IllegalStateException("请勿直接使用基类，请创建子类并重写 secretKey() 和 runJob() 方法");
        }

        String secretKey = secretKey();
        if (secretKey == null || secretKey.isEmpty() || secretKey.equals(DEFAULT_SECRET_KEY)) {
            log.error("[{}] 方法重写校验失败: {} 未正确重写 secretKey() 方法，返回了空或默认占位值", requestId, className);
            throw new IllegalStateException(className + " 的 secretKey() 方法未正确实现，请返回实际的密钥字符串");
        }

        log.debug("[{}] 方法重写校验通过: {}", requestId, className);
    }

    private void validateSecret(HttpServletRequest request) {
        String inputSecret = request.getHeader("X-Secret");
        if (inputSecret == null || !inputSecret.equals(secretKey())) {
            throw new SecurityException("密钥不匹配");
        }
    }

    protected ObjectMapper mapper() {
        return MAPPER_LOCAL.get();
    }
}
