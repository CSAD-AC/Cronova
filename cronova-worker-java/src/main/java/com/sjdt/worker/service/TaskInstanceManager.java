package com.sjdt.worker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.worker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 任务实例持久化（JSON 文件，目录：task_instances/{taskRunId}.json）
 * <p>
 * 对应 Python 执行器 task_instance_manager。
 */
@Slf4j
@Component
public class TaskInstanceManager {

    private final WorkerProperties properties;
    private final ObjectMapper objectMapper;

    public TaskInstanceManager(WorkerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    private Path instancePath(String taskRunId) {
        return Path.of(properties.getInstanceDir()).resolve(taskRunId + ".json");
    }

    public boolean create(String taskRunId, String taskId, String workerId, String scriptType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("task_run_id", taskRunId);
        data.put("task_id", taskId);
        data.put("status", "pending");
        data.put("created_time", utcNow());
        data.put("start_time", null);
        data.put("end_time", null);
        data.put("pause_time", null);
        data.put("resume_time", null);
        data.put("worker_id", workerId);
        data.put("script_type", scriptType);
        data.put("exit_code", null);
        data.put("error_message", null);
        data.put("duration_ms", null);
        data.put("pause_reason", null);
        data.put("process_id", null);
        return save(taskRunId, data);
    }

    public synchronized boolean updateStatus(String taskRunId, String status, Map<String, Object> extras) {
        Map<String, Object> data = load(taskRunId);
        if (data == null) {
            log.error("任务实例不存在，无法更新状态，taskRunId={}", taskRunId);
            return false;
        }
        data.put("status", status);
        String now = utcNow();
        // 状态机：running→start_time、paused→pause_time、终态(success/failed/timeout)→end_time、resumed→resume_time；extras 统一合并进实例数据
        switch (status) {
            case "running" -> data.put("start_time", now);
            case "paused" -> {
                data.put("pause_time", now);
                if (extras != null && extras.get("pause_reason") != null) {
                    data.put("pause_reason", extras.get("pause_reason"));
                }
            }
            case "success", "failed", "timeout" -> {
                data.put("end_time", now);
                if (extras != null) {
                    extras.forEach(data::put);
                }
            }
            case "resumed" -> data.put("resume_time", now);
            default -> {
                if (extras != null) {
                    extras.forEach(data::put);
                }
            }
        }
        if (extras != null) {
            extras.forEach(data::put);
        }
        data.put("last_updated", now);
        return save(taskRunId, data);
    }

    public Map<String, Object> load(String taskRunId) {
        Path path = instancePath(taskRunId);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (IOException e) {
            log.error("加载任务实例失败，taskRunId={}, err={}", taskRunId, e.getMessage());
            return null;
        }
    }

    public String getStatus(String taskRunId) {
        Map<String, Object> data = load(taskRunId);
        return data == null ? null : String.valueOf(data.get("status"));
    }

    public boolean delete(String taskRunId) {
        Path path = instancePath(taskRunId);
        try {
            if (Files.exists(path)) {
                Files.delete(path);
                log.info("任务实例已删除，taskRunId={}", taskRunId);
            }
            return true;
        } catch (IOException e) {
            log.error("删除任务实例失败，taskRunId={}, err={}", taskRunId, e.getMessage());
            return false;
        }
    }

    private boolean save(String taskRunId, Map<String, Object> data) {
        try {
            Path path = instancePath(taskRunId);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data),
                    StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            log.error("保存任务实例失败，taskRunId={}, err={}", taskRunId, e.getMessage());
            return false;
        }
    }

    private String utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC).toString();
    }
}
