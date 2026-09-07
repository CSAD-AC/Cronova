package com.cronova.worker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 任务执行消息（对应调度器下发的 TASK_EXECUTE 消息体）
 * <p>
 * 字段与调度器 TaskExecuteMessage / ScriptInfo 序列化保持一致：
 * script 内部使用 snake_case 键（is_directory / main_file / parent_id / root_path）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskExecuteMessage {

    private String taskId;
    private String taskName;
    private String taskRunId;
    private String description;
    private String scriptType;
    private ScriptInfo script;
    private String taskType;
    private String cronExpr;
    private String targetWorkerId;
    private Integer timeoutSeconds;

    /** 分片身份（日志聚合用；非分片任务为 null / 1） */
    private Integer shardIndex;
    private Integer shardTotal;
    private String shardBatchId;

    public int timeoutSecondsOrDefault() {
        return timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : 300;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScriptInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("path")
        private String path;

        @JsonProperty("is_directory")
        private Boolean isDirectory;

        @JsonProperty("main_file")
        private String mainFile;

        @JsonProperty("parent_id")
        private String parentId;

        @JsonProperty("root_path")
        private String rootPath;

        @JsonProperty("tags")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private Set<Tag> tags;
    }

    /** 脚本标签（只关心 name 做依赖匹配） */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Tag {
        private Integer id;
        private String name;
        private String operator;
        private String version;
    }

    /** 便捷：脚本依赖标签名列表 */
    public List<String> requiredTagNames() {
        if (script == null || script.getTags() == null) {
            return List.of();
        }
        return script.getTags().stream()
                .map(Tag::getName)
                .filter(n -> n != null && !n.isBlank())
                .map(String::toLowerCase)
                .toList();
    }
}
