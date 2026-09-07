package com.cronova.worker.common;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 运行时 Worker 上下文（注册后填充）
 * <p>
 * workerId / AK / SK / 标签在注册或启动流程中写入，后续只读。
 */
@Slf4j
@Component
public class WorkerContext {

    @Getter
    private volatile String workerId;
    @Getter
    private volatile String accessKey;
    @Getter
    private volatile String secretKey;
    @Getter
    private volatile String host;
    @Getter
    private volatile String ip;
    /** 完整标签（带版本，如 ["V1","Python3.11"]） */
    @Getter
    private volatile List<String> workerTags = List.of();
    /** 解析后的标签名列表（小写，用于任务依赖匹配） */
    @Getter
    private volatile List<String> tagNames = List.of();

    public void register(String workerId, String accessKey, String secretKey,
                         String host, String ip, List<String> workerTags) {
        this.workerId = workerId;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.host = host;
        this.ip = ip;
        this.workerTags = workerTags == null ? List.of() : List.copyOf(workerTags);
        this.tagNames = parseTagNames(this.workerTags);
        log.info("Worker 上下文已初始化，workerId={}, tags={}", workerId, this.workerTags);
    }

    public void updateTags(List<String> tags) {
        List<String> safe = tags == null ? List.of() : List.copyOf(tags);
        this.workerTags = safe;
        this.tagNames = parseTagNames(safe);
        log.info("Worker 标签已更新，tags={}", safe);
    }

    public boolean isRegistered() {
        return workerId != null && !workerId.isBlank();
    }

    /** 将带版本的标签解析为名称列表（小写），如 "Python3.11" -> "python3.11" */
    public static List<String> parseTagNames(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> {
                    String name = t.trim();
                    int idx = name.indexOf(':');
                    if (idx >= 0) {
                        name = name.substring(0, idx);
                    }
                    return name.toLowerCase();
                })
                .toList();
    }
}
