package com.sjdt.worker.service;

import com.sjdt.worker.client.AkskHttpClient;
import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.config.WorkerProperties;
import com.sjdt.worker.model.TaskResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 运行中任务状态持续上报（/api/worker/task-status/{taskRunId}）
 * <p>
 * 对应 Python 执行器 task_status_service：定期上报运行中任务状态，支持控制操作后即时上报。
 */
@Slf4j
@Component
public class TaskStatusReporter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final WorkerProperties properties;
    private final WorkerContext workerContext;
    private final ProcessManager processManager;
    private final TaskInstanceManager instanceManager;
    private final AkskHttpClient httpClient;

    public TaskStatusReporter(WorkerProperties properties,
                              WorkerContext workerContext,
                              ProcessManager processManager,
                              TaskInstanceManager instanceManager,
                              AkskHttpClient httpClient) {
        this.properties = properties;
        this.workerContext = workerContext;
        this.processManager = processManager;
        this.instanceManager = instanceManager;
        this.httpClient = httpClient;
    }

    @Scheduled(fixedDelayString = "${worker.task-status-interval-sec:5}000")
    public void scheduledReport() {
        reportAllRunning();
    }

    /** 上报所有运行中的任务状态（周期任务 + 控制操作后触发） */
    public void reportAllRunning() {
        if (!workerContext.isRegistered()) {
            return;
        }
        List<ProcessManager.RunningProcess> running = processManager.getAll();
        for (ProcessManager.RunningProcess rp : running) {
            reportOne(rp);
        }
    }

    private void reportOne(ProcessManager.RunningProcess rp) {
        String runId = rp.getTask().getTaskRunId();
        String currentStatus = instanceManager.getStatus(runId);
        if (currentStatus == null) {
            currentStatus = "running";
        }
        Instant start = rp.getStartTime();
        long durationMs = Duration.between(start, Instant.now()).toMillis();

        TaskResultMessage message = TaskResultMessage.builder()
                .workerId(workerContext.getWorkerId())
                .taskId(rp.getTask().getTaskId())
                .taskRunId(runId)
                .status(currentStatus)
                .exitCode(0)
                .startTime(LocalDateTime.ofInstant(start, ZoneOffset.UTC).format(FMT))
                .durationMs(durationMs)
                .logPath(rp.getLogBucket() + "/" + rp.getLogObject())
                .build();

        String url = properties.getSchedulerBaseUrl() + "/api/worker/task-status/" + runId;
        reportWithRetry(url, message, 3);
    }

    private void reportWithRetry(String url, TaskResultMessage message, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                AkskHttpClient.ApiResponse resp = httpClient.post(url, message, null);
                if (resp.isOk()) {
                    return;
                }
                log.error("状态上报业务处理失败（{}/{}），taskRunId={}, code={}, msg={}",
                        attempt, maxRetries, message.getTaskRunId(), resp.code(), resp.message());
            } catch (Exception e) {
                log.error("状态上报异常（{}/{}），taskRunId={}, err={}",
                        attempt, maxRetries, message.getTaskRunId(), e.getMessage());
            }
            if (attempt < maxRetries) {
                try {
                    Thread.sleep((long) Math.pow(2, attempt - 1) * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.error("状态上报最终失败，taskRunId={}", message.getTaskRunId());
    }
}
