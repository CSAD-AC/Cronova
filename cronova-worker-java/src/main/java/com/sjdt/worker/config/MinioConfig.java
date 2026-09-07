package com.sjdt.worker.config;

import com.sjdt.worker.client.MinioStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端装配
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioStorage minioStorage(WorkerProperties properties) {
        return new MinioStorage(properties);
    }
}
