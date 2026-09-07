package com.cronova.worker.service;

import com.cronova.worker.handler.TaskHandler;
import com.cronova.worker.model.TaskControlMessage;
import com.cronova.worker.model.TaskExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务服务：按脚本类型分发执行 + 处理控制指令（停止/暂停/恢复/强制终止/删除）
 */
@Slf4j
@Service
public class TaskService {

    private final Map<String, TaskHandler> handlerMap;
    private final ProcessManager processManager;
    private final TaskInstanceManager instanceManager;
    private final TaskStatusReporter statusReporter;

    public TaskService(List<TaskHandler> handlers,
                       ProcessManager processManager,
                       TaskInstanceManager instanceManager,
                       TaskStatusReporter statusReporter) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::supportType, Function.identity()));
        this.processManager = processManager;
        this.instanceManager = instanceManager;
        this.statusReporter = statusReporter;
    }

    /** 处理任务执行消息（提交到对应 handler，立即 ACK） */
    public boolean processTask(TaskExecuteMessage task) {
        TaskHandler handler = handlerMap.get(task.getScriptType());
        if (handler == null) {
            log.error("不支持的任务类型：{}，taskId={}", task.getScriptType(), task.getTaskId());
            return false;
        }
        return handler.handle(task);
    }

    /** 停止任务（reason 含"超时"时强制终止） */
    public boolean stopTask(TaskControlMessage message) {
        String runId = message.getTaskRunId();
        boolean timeoutStop = message.getReason() != null && message.getReason().contains("超时");
        log.info("停止任务，taskRunId={}, reason={}, force={}", runId, message.getReason(), timeoutStop);
        if (!processManager.isRunning(runId)) {
            log.warn("任务未在运行，taskRunId={}", runId);
            return true;
        }
        boolean success = processManager.stop(runId, timeoutStop);
        if (success) {
            instanceManager.updateStatus(runId, "failed",
                    Map.of("error_message", "任务被停止，原因：" + message.getReason()));
            statusReporter.reportAllRunning();
        }
        return success;
    }

    /** 强制终止任务 */
    public boolean killTask(TaskControlMessage message) {
        String runId = message.getTaskRunId();
        log.info("强制终止任务，taskRunId={}, reason={}", runId, message.getReason());
        if (!processManager.isRunning(runId)) {
            log.warn("任务未在运行，跳过强制终止，taskRunId={}", runId);
            return true;
        }
        boolean success = processManager.stop(runId, true);
        if (success) {
            instanceManager.updateStatus(runId, "failed",
                    Map.of("error_message", "任务被强制终止，原因：" + message.getReason()));
            statusReporter.reportAllRunning();
        }
        return success;
    }

    /** 暂停任务 */
    public boolean pauseTask(TaskControlMessage message) {
        String runId = message.getTaskRunId();
        if (!processManager.isRunning(runId)) {
            log.warn("任务进程不存在或已停止，无法暂停，taskRunId={}", runId);
            return false;
        }
        boolean success = processManager.pause(runId);
        if (success) {
            instanceManager.updateStatus(runId, "paused",
                    Map.of("pause_reason", message.getReason()));
            statusReporter.reportAllRunning();
        }
        return success;
    }

    /** 恢复任务 */
    public boolean resumeTask(TaskControlMessage message) {
        String runId = message.getTaskRunId();
        if (!processManager.isRunning(runId)) {
            log.warn("任务进程不存在或已停止，无法恢复，taskRunId={}", runId);
            return false;
        }
        boolean success = processManager.resume(runId);
        if (success) {
            instanceManager.updateStatus(runId, "running", Map.of());
            statusReporter.reportAllRunning();
        }
        return success;
    }

    /** 删除任务 */
    public boolean deleteTask(TaskControlMessage message) {
        String runId = message.getTaskRunId();
        log.info("删除任务，taskRunId={}", runId);
        if (processManager.isRunning(runId)) {
            processManager.stop(runId, true);
        }
        return instanceManager.delete(runId);
    }
}
