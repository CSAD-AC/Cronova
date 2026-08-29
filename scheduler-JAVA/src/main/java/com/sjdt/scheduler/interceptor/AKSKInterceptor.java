package com.sjdt.scheduler.interceptor;

import cn.hutool.core.io.IoUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.service.IAKSKService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * AKSK 拦截器
 * 用于拦截指定 URL 模式的请求，进行 AKSK 鉴权
 */
@Component
public class AKSKInterceptor implements HandlerInterceptor {

    @Autowired
    private IAKSKService akskService;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 请求头中的 Access Key 字段名
     */
    private static final String HEADER_ACCESS_KEY = "X-Access-Key";

    /**
     * 请求头中的 Signature 字段名
     */
    private static final String HEADER_SIGNATURE = "X-Signature";

    /**
     * 请求头中的 Timestamp 字段名
     */
    private static final String HEADER_TIMESTAMP = "X-Timestamp";

    /**
     * 在请求处理前进行 AKSK 鉴权
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param handler 处理程序
     * @return 鉴权结果，true 表示鉴权通过，false 表示鉴权失败
     * @throws Exception 异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求头获取 AKSK 相关信息
        String accessKey = request.getHeader(HEADER_ACCESS_KEY);
        String signature = request.getHeader(HEADER_SIGNATURE);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);

        // 2. 检查必填字段是否存在
        if (accessKey == null || signature == null || timestamp == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=utf-8");
            Result<Void> result = Result.error(HttpStatus.UNAUTHORIZED.value(), "Missing AKSK authentication headers");
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }

        // 3. 验证 AKSK 签名（不再需要请求体）
        boolean isValid = akskService.validateAKSK(accessKey, signature, timestamp, "");
        if (!isValid) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=utf-8");
            Result<Void> result = Result.error(HttpStatus.UNAUTHORIZED.value(), "Invalid AKSK authentication");
            response.getWriter().write(objectMapper.writeValueAsString(result));
            return false;
        }

        // 5. 鉴权通过，继续处理请求
        return true;
    }

}