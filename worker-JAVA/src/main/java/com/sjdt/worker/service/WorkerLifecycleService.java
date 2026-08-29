package com.sjdt.worker.service;

import com.sjdt.worker.client.AkskHttpClient;
import com.sjdt.worker.common.LocalConfigStore;
import com.sjdt.worker.common.TagManager;
import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.config.WorkerProperties;
import com.sjdt.worker.model.WorkerRegisterRequest;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * Worker 生命周期管理（启动注册流程 + 退出清理）
 * <p>
 * 启动：加载本地配置 → 验证 AK/SK → 无效则重新注册 → 初始化上下文 → 启动 MQ。
 * 退出：停止运行任务、关闭 MQ 消费者与结果生产者。
 */
@Slf4j
@Component
public class WorkerLifecycleService implements ApplicationRunner {

    private final WorkerProperties properties;
    private final WorkerContext workerContext;
    private final LocalConfigStore localConfigStore;
    private final TagManager tagManager;
    private final AkskHttpClient httpClient;
    private final MqManager mqManager;
    private final TaskResultPublisher resultPublisher;
    private final ProcessManager processManager;
    private final TagReporter tagReporter;

    public WorkerLifecycleService(WorkerProperties properties,
                                  WorkerContext workerContext,
                                  LocalConfigStore localConfigStore,
                                  TagManager tagManager,
                                  AkskHttpClient httpClient,
                                  MqManager mqManager,
                                  TaskResultPublisher resultPublisher,
                                  ProcessManager processManager,
                                  TagReporter tagReporter) {
        this.properties = properties;
        this.workerContext = workerContext;
        this.localConfigStore = localConfigStore;
        this.tagManager = tagManager;
        this.httpClient = httpClient;
        this.mqManager = mqManager;
        this.resultPublisher = resultPublisher;
        this.processManager = processManager;
        this.tagReporter = tagReporter;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始加载配置并初始化 Worker 上下文...");
        String host = localHost();
        String ip = localIp();
        List<String> tags = tagManager.loadTags();

        // 1. 加载本地配置（workerId / AK / SK）
        LocalConfigStore.LocalData local = localConfigStore.load();
        boolean hasValidLocal = local.getWorkerId() != null && !local.getWorkerId().isBlank()
                && local.getAccessKey() != null && !local.getAccessKey().isBlank()
                && local.getSecretKey() != null && !local.getSecretKey().isBlank();

        boolean needRegister = !hasValidLocal;
        if (hasValidLocal) {
            // 2. 验证 AK/SK 有效性
            workerContext.register(local.getWorkerId(), local.getAccessKey(), local.getSecretKey(),
                    host, ip, tags);
            boolean valid = validateAksk(local.getAccessKey());
            if (valid) {
                log.info("本地 AK/SK 验证通过，workerId={}", local.getWorkerId());
            } else {
                log.warn("本地 AK/SK 验证失败，触发重新注册");
                needRegister = true;
            }
        }

        // 3. 需要注册
        if (needRegister) {
            if (!registerWorker(host, ip, tags)) {
                log.error("Worker 注册失败，程序退出");
                throw new IllegalStateException("Worker 注册失败");
            }
        }

        // 4. 初始化标签基线 + 启动 MQ
        tagReporter.initReportedTags(workerContext.getWorkerTags());
        resultPublisher.start();
        mqManager.start();
        log.info("执行器启动完成，workerId={}, tags={}", workerContext.getWorkerId(), workerContext.getWorkerTags());
    }

    private boolean validateAksk(String accessKey) {
        String url = properties.getSchedulerBaseUrl() + "/api/worker/validate-aksk";
        AkskHttpClient.ApiResponse resp = httpClient.postUnsigned(url, null, Map.of("accessKey", accessKey));
        if (resp.isOk() && resp.data() != null) {
            JsonNode validNode = resp.data().get("valid");
            return validNode != null && validNode.asBoolean(false);
        }
        log.warn("AK/SK 验证请求失败，code={}, msg={}", resp.code(), resp.message());
        return false;
    }

    private boolean registerWorker(String host, String ip, List<String> tags) {
        String url = properties.getSchedulerBaseUrl() + "/api/worker/register";
        WorkerRegisterRequest request = WorkerRegisterRequest.builder()
                .host(host)
                .ip(ip)
                .tags(tags)
                .build();
        AkskHttpClient.ApiResponse resp = httpClient.postUnsigned(url, request, null);
        if (!resp.isOk() || resp.data() == null) {
            log.error("注册请求失败，code={}, msg={}", resp.code(), resp.message());
            return false;
        }
        JsonNode data = resp.data();
        String workerId = data.path("workerId").asText(null);
        String accessKey = data.path("accessKey").asText(null);
        String secretKey = data.path("secretKey").asText(null);
        if (workerId == null || accessKey == null || secretKey == null
                || workerId.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            log.error("调度器响应中缺少 WorkerID/ACCESS_KEY/SECRET_KEY");
            return false;
        }

        workerContext.register(workerId, accessKey, secretKey, host, ip, tags);

        LocalConfigStore.LocalData local = new LocalConfigStore.LocalData();
        local.setWorkerId(workerId);
        local.setAccessKey(accessKey);
        local.setSecretKey(secretKey);
        local.setWorkerTags(tags);
        localConfigStore.save(local);
        log.info("Worker 注册成功，ID={}", workerId);
        return true;
    }

    private String localHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String localIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "0.0.0.0";
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("收到关闭信号，开始清理...");
        processManager.shutdownAll();
        mqManager.shutdown();
        resultPublisher.shutdown();
        log.info("执行器清理完成");
    }
}
