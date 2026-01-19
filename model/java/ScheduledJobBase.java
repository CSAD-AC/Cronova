package com.sjdt.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDateTime;
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
 *     · 回调接口：/api/callback/data-sync
 *     · 健康接口：/api/callback/data-sync/health
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
            return Result.fail(400, "缺少请求头 X-Request-Id").setCostMillis(System.currentTimeMillis() - start);
        }

        try {
            validateMethodOverrides(requestId);
            validateSecret(request);
            Result<?> result = runJob(param == null ? Map.of() : param);
            if (result == null) result = Result.ok(null);
            result.setCostMillis(System.currentTimeMillis() - start);
            result.setRequestId(requestId);
            return result;
        } catch (SecurityException e) {
            log.warn("[{}] 密钥验证失败: {}", requestId, e.getMessage());
            return Result.fail(401, "密钥验证失败").setRequestId(requestId);
        } catch (IllegalStateException e) {
            log.error("[{}] 方法重写校验失败: {}", requestId, e.getMessage());
            return Result.fail(500, "配置错误: " + e.getMessage()).setRequestId(requestId);
        } catch (Exception e) {
            log.error("[{}] 任务执行出错", requestId, e);
            return Result.fail(500, "执行失败").setRequestId(requestId);
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
     *   "msg": "success",
     *   "data": {
     *     "status": "UP",
     *     "timestamp": "2024-01-19T10:30:00",
     *     "endpoint": "数据同步服务",
     *     "message": "服务正常"
     *   },
     *   "costMillis": 5,
     *   "requestId": "abc123def456"
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

        // 验证请求头
        if (requestId == null || requestId.isEmpty()) {
            return Result.fail(400, "缺少请求头 X-Request-Id")
                    .setCostMillis(System.currentTimeMillis() - start);
        }

        try {
            validateSecret(request);

            // 健康检查成功，构造响应数据
            Map<String, Object> healthData = new java.util.HashMap<>();
            healthData.put("status", "UP");
            healthData.put("timestamp", LocalDateTime.now().toString());
            healthData.put("endpoint", this.endpoint());
            healthData.put("message", "服务正常");

            log.debug("[{}] 健康检查通过，endpoint: {}", requestId, this.endpoint());

            return Result.ok(healthData)
                    .setCostMillis(System.currentTimeMillis() - start)
                    .setRequestId(requestId);

        } catch (SecurityException e) {
            log.warn("[{}] 健康检查失败: 密钥验证失败", requestId);
            return Result.fail(401, "密钥验证失败")
                    .setRequestId(requestId)
                    .setCostMillis(System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("[{}] 健康检查异常: {}", requestId, e.getMessage());
            return Result.fail(500, "健康检查异常: " + e.getMessage())
                    .setRequestId(requestId)
                    .setCostMillis(System.currentTimeMillis() - start);
        }
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

    /**
     * 统一返回对象
     */
    public static class Result<T> {
        private int code;
        private String msg;
        private T data;
        private long costMillis;
        private String requestId;

        public static <T> Result<T> ok(T data) {
            return new Result<T>().code(200).msg("success").data(data);
        }

        public static <T> Result<T> fail(int code, String msg) {
            return new Result<T>().code(code).msg(msg);
        }

        public Result<T> code(int code) { this.code = code; return this; }
        public Result<T> msg(String msg) { this.msg = msg; return this; }
        public Result<T> data(T data) { this.data = data; return this; }
        public Result<T> setCostMillis(long costMillis) { this.costMillis = costMillis; return this; }
        public Result<T> setRequestId(String requestId) { this.requestId = requestId; return this; }

        public int getCode() { return code; }
        public String getMsg() { return msg; }
        public T getData() { return data; }
        public long getCostMillis() { return costMillis; }
        public String getRequestId() { return requestId; }
    }

    private static class SecurityException extends RuntimeException {
        public SecurityException(String msg) { super(msg); }
    }
}
