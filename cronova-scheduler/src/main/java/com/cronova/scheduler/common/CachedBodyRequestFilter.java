package com.cronova.scheduler.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求体缓存过滤器
 * <p>
 * 仅对 Worker 鉴权接口（/api/worker/**，排除 register/validate-aksk）进行请求体缓存，
 * 供 AKSK 拦截器读取 body 参与签名校验，同时保证 Controller 仍可读取。
 * 其他路径（含大文件上传）不缓存，避免内存占用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CachedBodyRequestFilter implements Filter {

    /**
     * 过滤器入口：对需要 AKSK 鉴权的 Worker 请求包装为可重复读请求体
     * @param servletRequest  请求
     * @param servletResponse 响应
     * @param filterChain     过滤器链
     * @throws IOException  IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String uri = request.getRequestURI();

        // 仅对受 AKSK 校验的 Worker 接口缓存请求体
        boolean needCache = uri != null && uri.startsWith("/api/worker/")
                && !uri.endsWith("/register")
                && !uri.endsWith("/validate-aksk");

        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (needCache) {
            try {
                filterChain.doFilter(new CachedBodyHttpServletRequest(request), servletResponse);
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("request body exceeds")) {
                    response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                    response.setContentType("application/json;charset=utf-8");
                    response.getWriter().write("{\"code\":413,\"message\":\"request body too large\"}");
                    return;
                }
                throw e;
            }
        } else {
            filterChain.doFilter(request, servletResponse);
        }
    }
}
