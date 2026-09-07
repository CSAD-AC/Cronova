package com.cronova.worker.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cronova.worker.config.WorkerProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * 本地配置持久化（WorkerID / AK / SK / 标签）
 * <p>
 * 与 Python 执行器的 local_config.json 结构保持一致，便于互操作。
 */
@Slf4j
@Component
public class LocalConfigStore {

    private final WorkerProperties properties;
    private final ObjectMapper objectMapper;

    public LocalConfigStore(WorkerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Path configPath() {
        return Path.of(properties.getLocalConfigPath());
    }

    /** 是否存在本地配置 */
    public boolean exists() {
        return Files.exists(configPath());
    }

    /** 读取本地配置，不存在返回空配置 */
    public LocalData load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            log.warn("本地配置文件不存在，将执行注册流程，path={}", path.toAbsolutePath());
            return new LocalData();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, LocalData.class);
        } catch (IOException e) {
            log.error("加载本地配置失败，path={}, err={}", path, e.getMessage(), e);
            return new LocalData();
        }
    }

    /** 保存本地配置 */
    public boolean save(LocalData data) {
        try {
            Path path = configPath();
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            log.info("本地配置保存成功，path={}", path.toAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("保存本地配置失败，err={}", e.getMessage(), e);
            return false;
        }
    }

    @Data
    public static class LocalData {
        private String workerId;
        private String accessKey;
        private String secretKey;
        private List<String> workerTags = Collections.emptyList();
    }
}
