package com.sjdt.scheduler.domain.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

/**
 * 任务执行消息
 * 用于调度器向Worker发送任务执行请求
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecuteMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 任务名称 */
    private String taskName;

    /** 任务运行实例ID */
    private String taskRunId;

    /** 任务描述 */
    private String description;

    /** 脚本类型：java/python/go */
    private String scriptType;

    /** 脚本信息 */
    private ScriptInfo script;

    /** 任务类型：once / cron */
    private String taskType;

    /** Cron表达式（仅cron任务使用） */
    private String cronExpr;

    /** 指定Worker ID（为空表示公共任务） */
    private String targetWorkerId;

    /** 超时时间（秒，0表示不限制） */
    private Integer timeoutSeconds;

    /** 分片序号（从0开始；非分片任务固定为0） */
    private Integer shardIndex;

    /** 分片总数（非分片任务固定为1） */
    private Integer shardTotal;

    /** 分片批次ID（同一次触发的所有分片共享；非分片任务为该次run自身的批次ID） */
    private String shardBatchId;

    /**
     * 脚本信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScriptInfo implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 脚本ID */
        @JsonProperty("id")
        private String id;

        /** 脚本名称 */
        @JsonProperty("name")
        private String name;

        /** 脚本路径 */
        @JsonProperty("path")
        private String path;

        /** 是否为目录类型 */
        @JsonProperty("is_directory")
        private Boolean isDirectory;

        /** 目录类型时的主入口文件路径 */
        @JsonProperty("main_file")
        private String mainFile;

        /** 父脚本ID，用于构建层级关系 */
        @JsonProperty("parent_id")
        private String parentId;

        /** 根目录路径，用于下载整个目录 */
        @JsonProperty("root_path")
        private String rootPath;

        /** 脚本标签 */
        @JsonProperty("tags")
        private Set<Tag> tags;
    }
}