package com.sjdt.worker.common;

import com.sjdt.worker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Worker 标签加载（优先级：环境变量 > .env 文件 > local_config.json > 默认空）
 * <p>
 * 与 Python 执行器 Config.load_worker_tags 的语义保持一致。
 */
@Slf4j
@Component
public class TagManager {

    private final WorkerProperties properties;
    private final LocalConfigStore localConfigStore;

    public TagManager(WorkerProperties properties, LocalConfigStore localConfigStore) {
        this.properties = properties;
        this.localConfigStore = localConfigStore;
    }

    /** 从本地配置的 workerTags 字段取标签 */
    public List<String> loadFromLocalConfig() {
        LocalConfigStore.LocalData data = localConfigStore.load();
        if (data.getWorkerTags() != null && !data.getWorkerTags().isEmpty()) {
            log.info("[TagManager] 从配置文件加载标签: {}", data.getWorkerTags());
            return data.getWorkerTags();
        }
        return List.of();
    }

    /** 从 .env 文件读取 WORKER_TAGS 行 */
    public List<String> loadFromDotEnv() {
        // 候选路径（容器工作目录为 /app）：app/.env、docker/.env、./.env
        List<Path> candidates = List.of(
                Path.of("app", ".env"),
                Path.of("docker", ".env"),
                Path.of(".env")
        );
        for (Path path : candidates) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("WORKER_TAGS=")) {
                        String value = trimmed.substring("WORKER_TAGS=".length()).trim();
                        if (value.isEmpty()) {
                            continue;
                        }
                        List<String> tags = splitTags(value);
                        if (!tags.isEmpty()) {
                            log.info("[TagManager] 从 .env 文件加载标签: {} ({})", tags, path);
                            return tags;
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("[TagManager] 读取 .env 文件标签失败: {}", e.getMessage());
            }
        }
        return List.of();
    }

    /** 按优先级加载标签 */
    public List<String> loadTags() {
        // 1. 环境变量
        String envTags = System.getenv("WORKER_TAGS");
        if (envTags != null && !envTags.isBlank()) {
            List<String> tags = splitTags(envTags);
            if (!tags.isEmpty()) {
                log.info("[TagManager] 从环境变量加载标签: {}", tags);
                return tags;
            }
        }
        // 2. .env 文件
        List<String> fromDotEnv = loadFromDotEnv();
        if (!fromDotEnv.isEmpty()) {
            return fromDotEnv;
        }
        // 3. local_config.json
        List<String> fromLocal = loadFromLocalConfig();
        if (!fromLocal.isEmpty()) {
            return fromLocal;
        }
        // 4. 默认空
        log.info("[TagManager] 未配置 Worker 标签，将使用空标签（不限制任务匹配）");
        return List.of();
    }

    private List<String> splitTags(String value) {
        List<String> tags = new ArrayList<>();
        for (String t : value.split(",")) {
            String trimmed = t.trim();
            if (!trimmed.isEmpty()) {
                tags.add(trimmed);
            }
        }
        return tags;
    }

    /** 供测试使用：解析逗号分隔串 */
    public static List<String> splitTagsStatic(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .toList();
    }
}
