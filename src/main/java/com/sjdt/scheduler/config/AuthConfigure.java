package com.sjdt.scheduler.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.sjdt.scheduler.Interceptor.AKSKInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 权限拦截配置
 */
@Configuration
public class AuthConfigure implements WebMvcConfigurer {

    @Autowired
    private AKSKInterceptor akskInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Sa-Token 拦截器，用于前端接口的登录验证
        registry.addInterceptor(new SaInterceptor(handle -> {
                    StpUtil.checkLogin();
                }))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/api/auth/login",
                        "/api/auth/register",
                        // 排除 Worker 接口，这些接口使用 AKSK 鉴权
                        "/api/worker/**"
                );

        // 2. AKSK 拦截器，用于 Worker 执行器的 HTTP 通信鉴权
        registry.addInterceptor(akskInterceptor)
                .addPathPatterns("/api/worker/**")
                .excludePathPatterns("/api/worker/register");
    }
}
