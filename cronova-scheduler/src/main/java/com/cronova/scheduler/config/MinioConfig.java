package com.cronova.scheduler.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * minio配置类，返回一个封装好的MinioClient对象
 */
@Configuration
public class MinioConfig
{
    //minIo路径
    @Value("${minio.endpoint}")
    private String endpoint;
    
    //用户名
    @Value("${minio.accessKey}")
    private String accessKey;
    
    //密码
    @Value("${minio.secretKey}")
    private String secretKey;
    
    @Bean
    public MinioClient minioClient()
    {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
