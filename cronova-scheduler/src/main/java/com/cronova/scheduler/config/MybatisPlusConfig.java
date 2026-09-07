package com.cronova.scheduler.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Plus 配置类
 * <p>
 * 使用 Spring Boot 的自动配置，避免手动配置 SqlSessionFactory 导致的问题
 * </p>
 */
@Configuration
@EnableTransactionManagement
@MapperScan("com.cronova.scheduler.mapper")
public class MybatisPlusConfig {
    
    /**
     * 配置 MyBatis-Plus 分页插件
     * 用于支持分页查询并自动计算总记录数和总页数
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
