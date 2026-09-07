package com.cronova.worker.service;

import com.cronova.worker.client.AkskHttpClient;
import com.cronova.worker.common.SystemMetrics;
import com.cronova.worker.common.WorkerContext;
import com.cronova.worker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 心跳与系统状态上报
 * <p>
 * 心跳：POST /api/worker/heartbeat/{workerId}（间隔可配置，默认 2 秒）
 * 状态：POST /api/worker/status/{workerId}?cpuUsage=&memUsage=&runningTasks=（默认 10 秒）
 */
@Slf4j
@Component
public class HeartbeatService {

    private final WorkerProperties properties;
    private final WorkerContext workerContext;
    private final SystemMetrics systemMetrics;
    private final AkskHttpClient httpClient;

    public HeartbeatService(WorkerProperties properties,
                            WorkerContext workerContext,
                            SystemMetrics systemMetrics,
                            AkskHttpClient httpClient) {
        this.properties = properties;
        this.workerContext = workerContext;
        this.systemMetrics = systemMetrics;
        this.httpClient = httpClient;
    }

    @Scheduled(fixedDelayString = "${worker.heartbeat-interval-sec:2}000")
    public void heartbeat() {
        if (!workerContext.isRegistered()) {
            return;
        }
        String url = properties.getSchedulerBaseUrl() + "/api/worker/heartbeat/" + workerContext.getWorkerId();
        AkskHttpClient.ApiResponse resp = httpClient.post(url, Map.of(), null);
        if (!resp.isOk()) {
            log.warn("心跳上报失败，code={}, msg={}", resp.code(), resp.message());
        }
    }

    @Scheduled(fixedDelayString = "${worker.status-report-interval-sec:10}000")
    public void reportStatus() {
        if (!workerContext.isRegistered()) {
            return;
        }
        Map<String, String> params = Map.of(
                "cpuUsage", String.valueOf(systemMetrics.getCpuUsagePercent()),
                "memUsage", String.valueOf(systemMetrics.getMemUsagePercent()),
                "runningTasks", String.valueOf(systemMetrics.getRunningTasks()));
        String url = properties.getSchedulerBaseUrl() + "/api/worker/status/" + workerContext.getWorkerId();
        AkskHttpClient.ApiResponse resp = httpClient.post(url, Map.of(), params);
        if (!resp.isOk()) {
            log.warn("状态上报失败，code={}, msg={}", resp.code(), resp.message());
        }
    }
}
