package com.cronova.scheduler.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调度可观测指标 VO
 * <p>
 * 供 {@code GET /api/dashboard/metrics?minutes=N} 返回，统计窗口内（默认 60 分钟）
 * <b>首轮触发</b>（retryNum=0 或 NULL）的 task_run 聚合数据：
 * 触发延迟分位数、派发情况、路由策略命中分布、Worker 执行分布、状态计数、
 * 在线调度实例数与最近分片批次汇总——用于证明调度器改造效果（延迟/成功率/分布）。
 */
@Data
public class SchedulerMetricsVO {

    /** 统计窗口（分钟） */
    private int windowMinutes;

    /** 触发延迟分布（毫秒；负值=扫描窗口内提前派发） */
    private Latency latency;

    /** 派发情况汇总 */
    private Dispatch dispatch;

    /** 路由策略 -> 首轮 run 数（按任务配置的 route_strategy 归组） */
    private Map<String, Long> routeDistribution;

    /** Worker ID -> 首轮 run 数（已认领执行的 run 按 workerId 归组） */
    private Map<String, Long> workerDistribution;

    /** run 状态 -> 数量（含 queued/skipped/timeout 等全部状态） */
    private Map<String, Long> statusCounts;

    /** 在线调度实例数 */
    private int schedulerOnline;

    /** 最近分片批次汇总（按最近活跃降序，最多 10 批） */
    private List<ShardBatchSummary> recentShardBatches;

    /**
     * 触发延迟统计（毫秒）。
     * 仅统计携带理论触发时间的首轮 run；无数据时各分位数为 0。
     */
    @Data
    public static class Latency {
        /** 样本数（写入 trigger_latency_ms 的首轮 run 数） */
        private long count;
        /** P50 延迟（毫秒） */
        private double p50Ms;
        /** P95 延迟（毫秒） */
        private double p95Ms;
        /** P99 延迟（毫秒） */
        private double p99Ms;
        /** 平均延迟（毫秒） */
        private double avgMs;
        /** 最大延迟（毫秒） */
        private double maxMs;
    }

    /**
     * 派发情况汇总（窗口内首轮 run）。
     */
    @Data
    public static class Dispatch {
        /** 首轮 run 总数 */
        private long totalRuns;
        /** 已派发（dispatch_time 非空） */
        private long dispatched;
        /** 待派发（pending 且未派发，含 park 等待超时监控兜底） */
        private long pendingNotDispatched;
        /** 派发超时（timeout 且从未派发，即 park 后被超时监控判死） */
        private long parkTimeout;
    }

    /**
     * 分片批次汇总（窗口内的首轮分片 run 按 shardBatchId 聚合）。
     * 注意：窗口截断可能导致批次内 run 数少于 shardTotal，以 shardTotal 字段为准。
     */
    @Data
    public static class ShardBatchSummary {
        /** 分片批次ID */
        private String batchId;
        /** 任务ID */
        private String taskId;
        /** 分片总数（任务配置，固定不漂移） */
        private int shardTotal;
        /** 窗口内已终态的片数 */
        private long finished;
        /** 其中成功的片数 */
        private long successCount;
        /** 其中失败/超时的片数 */
        private long failedCount;
        /** 批次内最新创建时间 */
        private LocalDateTime lastActiveTime;
    }
}
