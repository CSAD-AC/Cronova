package com.sjdt.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


import java.util.Map;

/**
 * 定时任务统一入口基类
 * 1. 明文校验请求头：X-Secret 必须等于 headerKey 属性
 * 2. 提供模板方法 runJob 给子类实现业务
 * 3. 统一返回 Result
 */
@Slf4j
@RestController
public abstract class ScheduledJobBase {

    /** 返回值为密钥，子类需重写 */
    protected  abstract String headerKey();

    private final ObjectMapper mapper = new ObjectMapper();

    /** 子类实现具体业务 */
    protected abstract Result<?> runJob(Map<String, Object> param) throws Exception;

    /** 统一执行函数 */
    public Result<?> handle(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, Object> param) {
        long start = System.currentTimeMillis();
        try {
            // 1. 明文头校验
            String key = request.getHeader("X-Secret");
            if (key == null || !key.equals(headerKey())) {
                return Result.fail("秘钥验证错误");
            }

            // 2. 执行业务
            Result<?> result = runJob(param == null ? Map.of() : param);
            if (result == null) result = Result.ok(null);
            result.setCostMillis(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("任务执行出错", e);
            return Result.fail(e.getMessage());
        }
    }

    /* ===== 统一返回对象 ===== */
    @Data
    public static class Result<T> {
        private int code;
        private String msg;
        private T data;
        private long costMillis;

        public static <T> Result<T> ok(T data) {
            Result<T> r = new Result<>();
            r.code = 200;
            r.msg = "success";
            r.data = data;
            return r;
        }

        public static <T> Result<T> fail(String msg) {
            Result<T> r = new Result<>();
            r.code = 500;
            r.msg = msg;
            return r;
        }
    }
}