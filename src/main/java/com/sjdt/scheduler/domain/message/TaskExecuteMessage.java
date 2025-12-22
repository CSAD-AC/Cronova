package com.sjdt.scheduler.domain.message;

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
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskExecuteMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务ID */
    private String taskId;

    /** 任务名称 */
    private String taskName;

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
        private String id;

        /** 脚本名称 */
        private String name;

        /** 脚本路径 */
        private String path;

        /** 脚本标签 */
        private Set<Tag> tags;
    }
}