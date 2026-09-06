package com.sjdt.worker.service;

import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.handler.TaskHandler;
import com.sjdt.worker.model.TaskControlMessage;
import com.sjdt.worker.model.TaskExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务服务：按脚本类型分发执行 + 处理控制指令（停止/暂停/恢复/强制终止/删除）
 * <p>
 * 执行入口先经 {@link RunClaimService} 做 SETNX 执行权抢占（幂等兜底）：
 * MQ 为 at-least-once，同一 run 重复投递的消息因抢占失败直接 ACK 不重复执行
 * （与 worker-Python 的 process_task 行为对齐）。
 */
@Slf4j
@Service
public class TaskService {

    private final Map<String, TaskHandler> handlerMap;
    private final ProcessManager processManager;
    private final TaskInstanceManager instanceManager;
    private final TaskStatusReporter statusReporter;
    private final RunClaimService runClaimService;
    private final WorkerContext workerContext;

    public TaskService(List<TaskHandler> handlers,
                       ProcessManager processManager,
                       TaskInstanceManager instanceManager,
                       TaskStatusReporter statusReporter,
                       RunClaimService runClaimService,
                       WorkerContext workerContext) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(TaskHandler::supportType, Function.identity()));
        this.processManager = processManager;
        this.instanceManager = instanceManager;
        this.statusReporter = statusReporter;
        this.runClaimService = runClaimService;
        this.workerContext = workerContext;
    }

    /** 处理任务执行消息（幂等抢占后提交到对应 handler，立即 ACK） */
    public boolean processTask(TaskExecuteMessage task) {
        // 幂等执行权抢占（SETNX）：同一 run 只允许真正执行一次，
        // 重复投递的消息因抢占失败直接 ACK 但不重复跑。TTL = 任务超时 + 60，覆盖整个执行窗口。
        int claimTtl = (task.getTimeoutSeconds() != null ? task.getTimeoutSeconds() : 60) + 60;
        if (!runClaimService.acquireRunClaim(task.getTaskRunId(), workerContext.getWorkerId(), claimTtl)) {
            log.info("runId 已被认领（重复消息），跳过执行：{}", task.getTaskRunId());
            return true;
        }

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
