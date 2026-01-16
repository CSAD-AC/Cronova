package com.sjdt.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

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
 *   2. runJob(param)   - 执行业务逻辑，返回 Result
 *
 * 三、子类需开放的接口
 *   - 提供 POST 接口接收回调请求，内部调用 super.handle()
 *
 * 四、日志规范
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
    private static final String PLACEHOLDER_CLASS_NAME = "ScheduledJobBase";

    /**
     * 返回密钥，用于 X-Secret 校验
     * 子类必须重写此方法
     */
    protected abstract String secretKey();

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
     * @PostMapping("/job")
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
