package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.TaskExecuteMessage;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.route.WorkerRegistry;
import com.cronova.scheduler.route.WorkerSnapshot;
import com.cronova.scheduler.route.strategy.RouteStrategyRouter;
import com.cronova.scheduler.util.RocketMQDelayUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** 单片派发结果收集超时（秒）：防止单片 insert/MQ send 卡死无限阻塞调度线程 */
    private static final long SHARD_DISPATCH_GET_TIMEOUT_SECONDS = 30;

    private final WorkerRegistry workerRegistry;
    private final RouteStrategyRouter router;
    private final TaskProducerService taskProducerService;
    private final ObjectMapper objectMapper;
    private final TaskRunMapper taskRunMapper;

    /** 分片并行派发并行度；0 或负数表示自动按 CPU 核数 */
    @Value("${cronova.dispatch.shard-parallelism:0}")
    private int shardParallelism;

    /** 分片并行派发线程池（@PostConstruct 初始化，应用关闭时释放） */
    private ExecutorService shardDispatchPool;

    /**
     * 构造注入派发所需依赖。
     *
     * @param workerRegistry      Worker 在线快照注册表
     * @param router              路由策略路由
     * @param taskProducerService 任务消息生产者
     * @param objectMapper        JSON 序列化器
     * @param taskRunMapper       任务运行实例 Mapper
     */
    @Autowired
    public TaskDispatchService(WorkerRegistry workerRegistry,
                               RouteStrategyRouter router,
                               TaskProducerService taskProducerService,
                               ObjectMapper objectMapper,
                               TaskRunMapper taskRunMapper) {
        this.workerRegistry = workerRegistry;
        this.router = router;
        this.taskProducerService = taskProducerService;
        this.objectMapper = objectMapper;
        this.taskRunMapper = taskRunMapper;
    }

    /**
     * 初始化分片并行派发线程池。
     * 并行度取配置 cronova.dispatch.shard-parallelism，未配置时按 CPU 核数（最小 2）。
     */
    @PostConstruct
    public void initShardDispatchPool() {
        int parallelism = shardParallelism > 0
                ? shardParallelism
                : Math.max(2, Runtime.getRuntime().availableProcessors());
        AtomicInteger seq = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "shard-dispatch-" + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        shardDispatchPool = Executors.newFixedThreadPool(parallelism, factory);
        log.info("分片并行派发线程池已初始化，并行度: {}", parallelism);
    }

    /**
     * 应用关闭时释放分片派发线程池。
     */
    @PreDestroy
    public void shutdownShardDispatchPool() {
        if (shardDispatchPool != null) {
            shardDispatchPool.shutdown();
            log.info("分片并行派发线程池已关闭");
        }
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

    /**
     * 分片专属路由：把各分片均匀分散到候选 Worker，避免并行派发时全部选中同一台。
     * <p>
     * 通用 {@link #dispatch} 的 LEAST_LOAD 在分片批量并行投递时，会在同一负载快照上
     * 每片都选中"负载最低"的那一台，导致分片全挤到单机、失去并行意义。
     * 分片场景改为：按片序号轮询 {@code candidates[shardIndex % N]}，在不超过 Worker 数时
     * 尽量一片一台；仅当任务显式指定 {@code target_worker} 时，才尊重钉死配置（全片到该 Worker）。
     *
     * @param task        任务定义
     * @param message     待发送的分片任务消息
     * @param shardIndex  本片序号（0 起）
     * @param delaySeconds 延时秒数
     * @return true=已投递；false=无匹配 Worker，park（交派发超时监控兜底）
     */
    private boolean dispatchShard(Task task, TaskExecuteMessage message, int shardIndex, int delaySeconds) {
        // 钉死 Worker：尊重用户显式指定（与通用 dispatch 语义一致，全片到同一台）
        if (task.getTargetWorker() != null && !task.getTargetWorker().isBlank()) {
            return dispatch(task, message, delaySeconds);
        }
        List<WorkerSnapshot> candidates = workerRegistry.onlineWorkers();
        if (candidates.isEmpty()) {
            log.warn("无在线Worker，分片{} park", shardIndex);
            return false;
        }
        Set<String> requiredTags = parseRequiredTags(task.getRequiredTags());
        if (requiredTags != null && !requiredTags.isEmpty()) {
            List<WorkerSnapshot> tagged = workerRegistry.filterByTags(candidates, requiredTags);
            if (tagged.isEmpty()) {
                log.warn("无匹配标签{}的在线Worker，分片{} park", requiredTags, shardIndex);
                return false;
            }
            candidates = tagged;
        }
        // 按片序号轮询分散，充分并行利用多 Worker
        WorkerSnapshot selected = candidates.get(shardIndex % candidates.size());
        send(message, selected.id(), delaySeconds);
        return true;
    }

    /**
     * 分片触发派发：shard_total &gt; 0 的任务一次触发生成 N 条 run + N 条 Tag 定向消息
     * <p>
     * 关键设计决策：shardTotal 固定（绝不动态缩片）——分片数不随在线 Worker 数漂移，
     * 否则 hash(主键) % shardTotal 的数据切分会错乱，导致重复处理或漏处理。
     * 每片独立走 dispatch()：由任务配置的路由策略为该片选 Worker（片数可大于 Worker 数，一台多片）；
     * 某片无可用 Worker 时该片 run 保持 pending（park），交派发超时监控兜底，不影响其他片。
     * <p>
     * 并行化：各分片之间互不依赖，通过共享线程池（{@link #shardDispatchPool}）并行执行
     * "建 run + 路由派发"，显著降低大分片数（如 ≥16）下的单次触发耗时。
     * 单片 insert/send 异常被隔离在 {@link #dispatchOneShard} 内，不影响其他分片。
     *
     * @param task          任务定义（shardTotal &gt; 0）
     * @param template      消息模板（taskRunId/shardIndex/shardTotal/shardBatchId 由本方法按片覆写）
     * @param shardTotal    分片总数
     * @param retryNum      重试次数（首轮触发为 0）
     * @param delaySeconds  延时秒数；&gt;0 时走 RocketMQ 延迟级别
     * @return true=至少一片已投递；false=所有片均 park
     */
    public boolean dispatchSharded(Task task, TaskExecuteMessage template,
                                   int shardTotal, int retryNum, int delaySeconds) {
        if (shardTotal < 0 || shardTotal > 256) {
            throw new IllegalArgumentException("shardTotal must be between 0 and 256");
        }
        String batchId = UUID.randomUUID().toString().replace("-", "");

        // 1. 并行提交各分片任务（每片：建 run + 路由派发），分片间互不依赖
        List<Future<Boolean>> futures = new ArrayList<>(shardTotal);
        for (int i = 0; i < shardTotal; i++) {
            final int shardIndex = i;
            futures.add(shardDispatchPool.submit(() -> dispatchOneShard(
                    task, template, batchId, shardIndex, shardTotal, retryNum, delaySeconds)));
        }

        // 2. 收集各片投递结果（带超时兜底：单片 MQ send 卡死时不阻塞调度线程）
        int dispatchedCount = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (Boolean.TRUE.equals(future.get(SHARD_DISPATCH_GET_TIMEOUT_SECONDS, TimeUnit.SECONDS))) {
                    dispatchedCount++;
                }
            } catch (TimeoutException e) {
                // 超时片取消任务；若 run 已插入则保持 pending，交由派发超时监控兜底
                future.cancel(true);
                log.error("分片派发超时（>{}s），已取消该片，任务ID: {}, 批次: {}",
                        SHARD_DISPATCH_GET_TIMEOUT_SECONDS, task.getId(), batchId);
            } catch (Exception e) {
                log.error("分片派发结果收集异常，任务ID: {}", task.getId(), e);
            }
        }
        boolean anyDispatched = dispatchedCount > 0;
        log.info("分片批次派发完成，任务ID: {}, 批次: {}, 总片数: {}, 已投递: {}, 至少一片投递: {}",
                task.getId(), batchId, shardTotal, dispatchedCount, anyDispatched);
        return anyDispatched;
    }

    /**
     * 派发单个分片：建 run 记录 + 构造分片消息 + 按路由定向投递。
     * <p>
     * 单片异常隔离：insert 或 send 抛异常时仅记录日志并返回 false，
     * 不会中断同一批次其他分片的派发（比串行实现更健壮）。
     *
     * @param task         任务定义
     * @param template     消息模板
     * @param batchId      分片批次 ID
     * @param shardIndex   本片序号（0 起）
     * @param shardTotal   分片总数
     * @param retryNum     重试次数
     * @param delaySeconds 延时秒数
     * @return true=本片已投递；false=本片 park 或派发异常
     */
    private boolean dispatchOneShard(Task task, TaskExecuteMessage template, String batchId,
                                     int shardIndex, int shardTotal, int retryNum, int delaySeconds) {
        try {
            TaskRun run = TaskRun.builder()
                    .taskId(task.getId())
                    .status(TaskRunStatus.pending)
                    .retryNum(retryNum)
                    .shardIndex(shardIndex)
                    .shardTotal(shardTotal)
                    .shardBatchId(batchId)
                    .startTime(LocalDateTime.now())
                    .createTime(LocalDateTime.now())
                    .build();
            taskRunMapper.insert(run);

            TaskExecuteMessage shardMessage = template.toBuilder()
                    .taskRunId(run.getId())
                    .shardIndex(shardIndex)
                    .shardTotal(shardTotal)
                    .shardBatchId(batchId)
                    .build();

            boolean dispatched = dispatchShard(task, shardMessage, shardIndex, delaySeconds);
            if (dispatched) {
                log.info("分片已定向派发，任务ID: {}, 批次: {}, 分片: {}/{}, 运行ID: {}",
                        task.getId(), batchId, shardIndex, shardTotal - 1, run.getId());
            } else {
                log.warn("分片无可用Worker已park，任务ID: {}, 批次: {}, 分片: {}/{}, 运行ID: {}（交派发超时监控兜底）",
                        task.getId(), batchId, shardIndex, shardTotal - 1, run.getId());
            }
            return dispatched;
        } catch (Exception e) {
            // 单片异常隔离：不影响其他分片的派发
            log.error("分片派发异常，任务ID: {}, 批次: {}, 分片: {}/{}",
                    task.getId(), batchId, shardIndex, shardTotal - 1, e);
            return false;
        }
    }

    private void send(TaskExecuteMessage message, String workerId, int delaySeconds) {
        if (delaySeconds > 0) {
            taskProducerService.sendTaskWithDelay(message, workerId, RocketMQDelayUtil.getDelayLevel(delaySeconds));
        } else {
            taskProducerService.sendTask(message, workerId);
        }
        recordDispatchMetrics(message);
    }

    /**
     * 派发成功后回写 run 的可观测指标（不阻塞主流程，失败仅告警）：
     * <ul>
     *     <li>{@code dispatch_time}：实际派发时间（park/未派发的 run 保持 NULL，即"未派发"信号）；</li>
     *     <li>{@code trigger_latency_ms}：派发时间 - 消息携带的理论触发时间；消息未携带（重试/重发/
     *         排队推进）时不写，避免重试等待期污染调度延迟指标。负值合法：扫描窗口提前量内
     *         任务可能在理论触发点之前被派发。</li>
     * </ul>
     *
     * @param message 已投递成功的任务执行消息（含 taskRunId 与可选 triggerTime）
     */
    private void recordDispatchMetrics(TaskExecuteMessage message) {
        if (message.getTaskRunId() == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            Long latencyMs = message.getTriggerTime() != null
                    ? Duration.between(message.getTriggerTime(), now).toMillis()
                    : null;
            taskRunMapper.update(null, new LambdaUpdateWrapper<TaskRun>()
                    .eq(TaskRun::getId, message.getTaskRunId())
                    .set(TaskRun::getDispatchTime, now)
                    .set(latencyMs != null, TaskRun::getTriggerLatencyMs, latencyMs));
        } catch (Exception e) {
            log.warn("回写派发指标失败（不影响派发结果），RunID: {}", message.getTaskRunId(), e);
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
            throw new IllegalArgumentException("invalid requiredTags: " + requiredTagsJson, e);
        }
    }
}
