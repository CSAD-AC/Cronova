package com.sjdt.worker.handler;

import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.model.TaskExecuteMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 依赖验证：检查脚本标签要求与 Worker 标签是否匹配（名称存在性语义）。
 * <p>
 * 与 Python 执行器 _validate_dependencies 保持一致：worker 标签即"已具备能力"集合，
 * 脚本要求的每个标签名（小写）必须出现在 worker 标签名集合中；不做真实版本探测。
 */
@Slf4j
public final class DependencyValidator {

    private DependencyValidator() {
    }

    public static void validate(TaskExecuteMessage task, WorkerContext workerContext) {
        List<String> required = task.requiredTagNames();
        if (required.isEmpty()) {
            log.info("任务 {} 无依赖标签要求，跳过依赖验证", task.getTaskId());
            return;
        }
        log.info("开始验证任务 {} 的依赖要求，taskRunId={}", task.getTaskId(), task.getTaskRunId());

        List<String> workerTagNames = workerContext.getTagNames();
        List<String> missing = new ArrayList<>();
        for (String tag : required) {
            boolean found = workerTagNames.stream()
                    .anyMatch(w -> w.equals(tag.toLowerCase(Locale.ROOT)));
            if (!found) {
                missing.add(tag);
                log.warn("缺少必需依赖：{}", tag);
            }
        }
        if (!missing.isEmpty()) {
            String error = "缺少必需依赖：" + String.join(", ", missing);
            log.error("依赖验证失败：{}", error);
            throw new IllegalStateException("依赖验证失败：" + error);
        }
        log.info("任务 {} 依赖验证通过，taskRunId={}", task.getTaskId(), task.getTaskRunId());
    }
}
