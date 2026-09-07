package com.cronova.scheduler.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    /** 安全方案名称，后面接口引用时保持一致 */
    private static final String SECURITY_SCHEME_NAME = "Sa-Token";

    @Bean
    public OpenAPI schedulerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("调度器接口")
                        .description("调度器服务，对接前端接口")
                        .version("v1.0.0"))
                // 注册全局安全需求
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                // 注册安全方案：APIKey 放在 Header
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .description("直接输入 Sa-Token 值即可，无需加 Bearer 前缀")));
    }
}