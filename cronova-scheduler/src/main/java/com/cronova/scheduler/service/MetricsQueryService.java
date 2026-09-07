package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.common.SchedulerInstanceRegistry;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.domain.vo.SchedulerMetricsVO;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 调度可观测指标查询服务
 * <p>
 * 聚合统计窗口内<b>首轮触发</b>（retryNum=0 或 NULL）的 task_run，产出：
 * 触发延迟 P50/P95/P99、派发情况（派发数 / park 超时数）、路由策略命中分布、
 * Worker 执行分布、状态计数、最近分片批次汇总、在线调度实例数。
 * <p>
 * 口径说明：
 * <ul>
 *     <li>重试/重发产生的 run（retryNum &gt; 0）被排除——重试等待期不属于调度延迟，
 *         排除后指标才能反映"从理论触发点到实际派发"的链路质量；</li>
 *     <li>窗口按 run 的 createTime 过滤；分片批次可能被窗口截断（批内 run 数 &lt; shardTotal），
 *         汇总以任务配置的 shardTotal 为准。</li>
 * </ul>
 * <p>
 * 使用方法：由 DashboardController 调用 {@link #query(int)}，minutes 自动收敛到 [1, 1440]。
 */
@Slf4j
@Service
public class MetricsQueryService {

    /** 统计窗口下限（分钟） */
    private static final int MIN_WINDOW_MINUTES = 1;

    /** 统计窗口上限（分钟，=24h） */
    private static final int MAX_WINDOW_MINUTES = 1440;

    /** 最近分片批次展示上限 */
    private static final int RECENT_BATCH_LIMIT = 10;

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;

    /** 任务定义Mapper（路由策略归组） */
    private final TaskMapper taskMapper;

    /** 调度实例注册表（集群视图） */
    private final SchedulerInstanceRegistry schedulerInstanceRegistry;

    @Autowired
    public MetricsQueryService(TaskRunMapper taskRunMapper, TaskMapper taskMapper,
                               SchedulerInstanceRegistry schedulerInstanceRegistry) {
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.schedulerInstanceRegistry = schedulerInstanceRegistry;
    }

    /**
     * 查询指定窗口内的调度可观测指标。
     *
     * @param minutes 统计窗口（分钟），自动收敛到 [1, 1440]
     * @return 聚合指标
     */
    public SchedulerMetricsVO query(int minutes) {
        int window = Math.max(MIN_WINDOW_MINUTES, Math.min(MAX_WINDOW_MINUTES, minutes));
        LocalDateTime since = LocalDateTime.now().minusMinutes(window);

        // 窗口内首轮 run（只取统计所需列，减少内存与网络开销）
        List<TaskRun> runs = taskRunMapper.selectList(new LambdaQueryWrapper<TaskRun>()
                .ge(TaskRun::getCreateTime, since)
                .and(w -> w.isNull(TaskRun::getRetryNum).or().eq(TaskRun::getRetryNum, 0))
                .select(TaskRun::getId, TaskRun::getTaskId, TaskRun::getStatus, TaskRun::getWorkerId,
                        TaskRun::getDispatchTime, TaskRun::getTriggerLatencyMs,
                        TaskRun::getShardTotal, TaskRun::getShardBatchId, TaskRun::getCreateTime));

        SchedulerMetricsVO vo = new SchedulerMetricsVO();
        vo.setWindowMinutes(window);
        vo.setLatency(buildLatency(runs));
        vo.setDispatch(buildDispatch(runs));
        vo.setRouteDistribution(buildRouteDistribution(runs));
        vo.setWorkerDistribution(buildWorkerDistribution(runs));
        vo.setStatusCounts(buildStatusCounts(runs));
        vo.setRecentShardBatches(buildShardBatches(runs));
        vo.setSchedulerOnline(schedulerInstanceRegistry.onlineCount());
        return vo;
    }

    /**
     * 计算触发延迟分位数：对窗口内携带 trigger_latency_ms 的样本升序排序取序统计量。
     * 负值（扫描窗口内提前派发）原样参与统计——提前派发是调度器特性，不应对指标做美化。
     *
     * @param runs 窗口内首轮 run
     * @return 延迟分布（无样本时各分位数为 0）
     */
    private SchedulerMetricsVO.Latency buildLatency(List<TaskRun> runs) {
        SchedulerMetricsVO.Latency latency = new SchedulerMetricsVO.Latency();
        List<Long> samples = runs.stream()
                .map(TaskRun::getTriggerLatencyMs)
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        latency.setCount(samples.size());
        if (samples.isEmpty()) {
            return latency;
        }
        latency.setP50Ms(percentile(samples, 0.50));
        latency.setP95Ms(percentile(samples, 0.95));
        latency.setP99Ms(percentile(samples, 0.99));
        latency.setAvgMs(samples.stream().mapToLong(Long::longValue).average().orElse(0));
        latency.setMaxMs(samples.get(samples.size() - 1));
        return latency;
    }

    /**
     * 取升序样本的 p 分位序统计量（nearest-rank 法：ceil(p*n)-1 下标）。
     *
     * @param sorted 升序样本
     * @param p      分位数（0~1）
     * @return 对应分位值
     */
    private double percentile(List<Long> sorted, double p) {
        int rank = (int) Math.ceil(p * sorted.size());
        int index = Math.max(0, Math.min(sorted.size() - 1, rank - 1));
        return sorted.get(index);
    }

    /**
     * 汇总派发情况：已派发 / 待派发（含 park 等待中）/ park 超时。
     *
     * @param runs 窗口内首轮 run
     * @return 派发汇总
     */
    private SchedulerMetricsVO.Dispatch buildDispatch(List<TaskRun> runs) {
        SchedulerMetricsVO.Dispatch dispatch = new SchedulerMetricsVO.Dispatch();
        dispatch.setTotalRuns(runs.size());
        dispatch.setDispatched(runs.stream().filter(r -> r.getDispatchTime() != null).count());
        dispatch.setPendingNotDispatched(runs.stream()
                .filter(r -> r.getDispatchTime() == null && r.getStatus() == TaskRunStatus.pending)
                .count());
        dispatch.setParkTimeout(runs.stream()
                .filter(r -> r.getDispatchTime() == null && r.getStatus() == TaskRunStatus.timeout)
                .count());
        return dispatch;
    }

    /**
     * 路由策略命中分布：窗口内 run 按所属任务的 route_strategy 归组计数
     * （任务级策略在窗口内多次触发会累计多份 run，符合"命中分布"语义）。
     *
     * @param runs 窗口内首轮 run
     * @return 策略名 -> run 数（按计数降序）
     */
    private Map<String, Long> buildRouteDistribution(List<TaskRun> runs) {
        Set<String> taskIds = runs.stream()
                .map(TaskRun::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> taskRoute = taskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(Task::getId,
                        t -> t.getRouteStrategy() != null ? t.getRouteStrategy() : "UNKNOWN"));
        Map<String, Long> distribution = runs.stream()
                .filter(r -> r.getTaskId() != null)
                .collect(Collectors.groupingBy(
                        r -> taskRoute.getOrDefault(r.getTaskId(), "UNKNOWN"), Collectors.counting()));
        return distribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * Worker 执行分布：窗口内已认领执行（workerId 非空）的 run 按 Worker 归组计数，
     * 用于观察路由策略的实际打散效果。
     *
     * @param runs 窗口内首轮 run
     * @return workerId -> run 数（按计数降序）
     */
    private Map<String, Long> buildWorkerDistribution(List<TaskRun> runs) {
        Map<String, Long> distribution = runs.stream()
                .filter(r -> r.getWorkerId() != null && !r.getWorkerId().isEmpty())
                .collect(Collectors.groupingBy(TaskRun::getWorkerId, Collectors.counting()));
        return distribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /**
     * 状态计数：窗口内首轮 run 按状态全量归组（含 queued/skipped 等）。
     *
     * @param runs 窗口内首轮 run
     * @return 状态名 -> 数量
     */
    private Map<String, Long> buildStatusCounts(List<TaskRun> runs) {
        return runs.stream()
                .filter(r -> r.getStatus() != null)
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), Collectors.counting()));
    }

    /**
     * 最近分片批次汇总：窗口内 shardTotal &gt; 1 的 run 按 shardBatchId 聚合，
     * 输出最近活跃的批次（片数 / 成功 / 失败），供压测与运维快速核对批次健康度。
     *
     * @param runs 窗口内首轮 run
     * @return 最近批次列表（最多 {@value #RECENT_BATCH_LIMIT} 批）
     */
    private List<SchedulerMetricsVO.ShardBatchSummary> buildShardBatches(List<TaskRun> runs) {
        Map<String, List<TaskRun>> batches = runs.stream()
                .filter(r -> r.getShardTotal() != null && r.getShardTotal() > 1
                        && r.getShardBatchId() != null)
                .collect(Collectors.groupingBy(TaskRun::getShardBatchId));
        return batches.values().stream()
                .map(batchRuns -> {
                    TaskRun first = batchRuns.get(0);
                    SchedulerMetricsVO.ShardBatchSummary summary = new SchedulerMetricsVO.ShardBatchSummary();
                    summary.setBatchId(first.getShardBatchId());
                    summary.setTaskId(first.getTaskId());
                    summary.setShardTotal(first.getShardTotal() != null ? first.getShardTotal() : 0);
                    summary.setFinished(batchRuns.stream().filter(this::isTerminal).count());
                    summary.setSuccessCount(batchRuns.stream()
                            .filter(r -> r.getStatus() == TaskRunStatus.success).count());
                    summary.setFailedCount(batchRuns.stream().filter(r ->
                            r.getStatus() == TaskRunStatus.failed || r.getStatus() == TaskRunStatus.timeout)
                            .count());
                    summary.setLastActiveTime(batchRuns.stream()
                            .map(TaskRun::getCreateTime)
                            .filter(Objects::nonNull)
                            .max(Comparator.naturalOrder())
                            .orElse(null));
                    return summary;
                })
                .sorted(Comparator.comparing(SchedulerMetricsVO.ShardBatchSummary::getLastActiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_BATCH_LIMIT)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 判断 run 是否处于终态（success/failed/timeout）。
     *
     * @param run 任务运行实例
     * @return true=终态
     */
    private boolean isTerminal(TaskRun run) {
        return run.getStatus() == TaskRunStatus.success
                || run.getStatus() == TaskRunStatus.failed
                || run.getStatus() == TaskRunStatus.timeout;
    }
}
