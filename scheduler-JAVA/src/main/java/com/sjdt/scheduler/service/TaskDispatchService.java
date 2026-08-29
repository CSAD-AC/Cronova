package com.sjdt.scheduler.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.route.WorkerRegistry;
import com.sjdt.scheduler.route.WorkerSnapshot;
import com.sjdt.scheduler.route.strategy.RouteStrategyRouter;
import com.sjdt.scheduler.util.RocketMQDelayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 统一派发入口：路由选 Worker + Tag 定向投递 + park 兜底
 * <p>
 * cron 触发 / 立即执行 / 重试三条派发路径都收敛到这里，路由权统一收归调度侧：
 * <ul>
 *     <li>任务钉死 targetWorker：该 Worker 在线则直投，不在线则 park（不盲发）；</li>
 *     <li>否则从在线 Worker 中按 requiredTags 过滤，再按任务配置的路由策略选一个；</li>
 *     <li>无可用 Worker / 路由无结果 -> park：不发消息，run 保持 pending，
 *         由派发超时监控（TaskTimeoutMonitor 120s）兜底重试。</li>
 * </ul>
 */
@Slf4j
@Component
public class TaskDispatchService {

    private final WorkerRegistry workerRegistry;
    private final RouteStrategyRouter router;
    private final TaskProducerService taskProducerService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TaskDispatchService(WorkerRegistry workerRegistry,
                               RouteStrategyRouter router,
                               TaskProducerService taskProducerService,
                               ObjectMapper objectMapper) {
        this.workerRegistry = workerRegistry;
        this.router = router;
        this.taskProducerService = taskProducerService;
        this.objectMapper = objectMapper;
    }

    /**
     * 路由选 Worker 并按 Tag 定向投递
     *
     * @param task        任务定义
     * @param message     待发送的任务执行消息（含 taskRunId）
     * @param delaySeconds 延时秒数；&gt;0 时走 RocketMQ 延迟级别
     * @return true=已投递；false=无可执行 Worker，已 park（run 保持 pending）
     */
    public boolean dispatch(Task task, TaskExecuteMessage message, int delaySeconds) {
        // 1. 钉死 Worker：在线则直投，不在线则 park（不再盲发到公共队列）
        if (task.getTargetWorker() != null && !task.getTargetWorker().isBlank()) {
            if (workerRegistry.isOnline(task.getTargetWorker())) {
                send(message, task.getTargetWorker(), delaySeconds);
                return true;
            }
            log.warn("目标Worker {} 不在线，任务{} park（run保持pending，交派发超时监控）",
                    task.getTargetWorker(), task.getId());
            return false;
        }

        // 2. 在线快照
        List<WorkerSnapshot> candidates = workerRegistry.onlineWorkers();
        if (candidates.isEmpty()) {
            log.warn("无在线Worker，任务{} park", task.getId());
            return false;
        }

        // 3. requiredTags 过滤（无论何种路由策略都生效）
        Set<String> requiredTags = parseRequiredTags(task.getRequiredTags());
        if (requiredTags != null && !requiredTags.isEmpty()) {
            List<WorkerSnapshot> tagged = workerRegistry.filterByTags(candidates, requiredTags);
            if (tagged.isEmpty()) {
                log.warn("无匹配标签{}的在线Worker，任务{} park", requiredTags, task.getId());
                return false;
            }
            candidates = tagged;
        }

        // 4. 路由策略选 Worker
        WorkerSnapshot selected = router.select(task, candidates);
        if (selected == null) {
            log.warn("路由未选出Worker，任务{} park", task.getId());
            return false;
        }

        send(message, selected.id(), delaySeconds);
        return true;
    }

    private void send(TaskExecuteMessage message, String workerId, int delaySeconds) {
        if (delaySeconds > 0) {
            taskProducerService.sendTaskWithDelay(message, workerId, RocketMQDelayUtil.getDelayLevel(delaySeconds));
        } else {
            taskProducerService.sendTask(message, workerId);
        }
    }

    private Set<String> parseRequiredTags(String requiredTagsJson) {
        if (requiredTagsJson == null || requiredTagsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(requiredTagsJson, new TypeReference<Set<String>>() {});
        } catch (Exception e) {
            log.warn("解析任务requiredTags失败: {}", requiredTagsJson, e);
            return null;
        }
    }
}
