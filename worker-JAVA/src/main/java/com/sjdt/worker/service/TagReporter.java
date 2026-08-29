package com.sjdt.worker.service;

import com.sjdt.worker.client.AkskHttpClient;
import com.sjdt.worker.common.LocalConfigStore;
import com.sjdt.worker.common.TagManager;
import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.config.WorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 标签动态检测与上报（/api/worker/tags/{workerId}）
 * <p>
 * 对应 Python 执行器 tag_report_service：周期性重载标签来源，变化时上报调度器并持久化。
 */
@Slf4j
@Component
public class TagReporter {

    private final WorkerProperties properties;
    private final WorkerContext workerContext;
    private final TagManager tagManager;
    private final LocalConfigStore localConfigStore;
    private final AkskHttpClient httpClient;

    private volatile Set<String> lastReportedTags = Set.of();

    public TagReporter(WorkerProperties properties,
                       WorkerContext workerContext,
                       TagManager tagManager,
                       LocalConfigStore localConfigStore,
                       AkskHttpClient httpClient) {
        this.properties = properties;
        this.workerContext = workerContext;
        this.tagManager = tagManager;
        this.localConfigStore = localConfigStore;
        this.httpClient = httpClient;
    }

    @Scheduled(fixedDelayString = "${worker.tag-check-interval-sec:60}000")
    public void checkAndReport() {
        if (!workerContext.isRegistered()) {
            return;
        }
        List<String> current = tagManager.loadTags();
        Set<String> currentSet = new HashSet<>(current);
        if (currentSet.equals(lastReportedTags)) {
            return;
        }
        log.info("检测到标签变化，旧: {}, 新: {}", lastReportedTags, currentSet);

        String url = properties.getSchedulerBaseUrl() + "/api/worker/tags/" + workerContext.getWorkerId();
        AkskHttpClient.ApiResponse resp = httpClient.post(url, Map.of("tags", current), null);
        if (resp.isOk()) {
            lastReportedTags = currentSet;
            workerContext.updateTags(current);
            saveToLocalConfig(current);
            log.info("标签上报成功，tags={}", current);
        } else {
            log.error("标签上报失败，code={}, msg={}", resp.code(), resp.message());
        }
    }

    /** 注册成功后初始化已上报标签基线 */
    public void initReportedTags(List<String> tags) {
        this.lastReportedTags = new HashSet<>(tags == null ? List.of() : tags);
        log.info("已初始化上报标签基线: {}", lastReportedTags);
    }

    private void saveToLocalConfig(List<String> tags) {
        LocalConfigStore.LocalData data = localConfigStore.load();
        data.setWorkerTags(tags);
        localConfigStore.save(data);
    }
}
