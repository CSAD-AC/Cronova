package com.cronova.scheduler.route.strategy;

import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 最少负载策略：按真实负载打分选最闲的 Worker
 * <p>
 * score = 0.4*cpu + 0.3*mem + 0.3*100*running/maxConcurrent，取最小；
 * 权重系数集中为常量，便于调参。
 * <p>
 * 打散说明：突发下多个 Worker 的负载上报同分（均报闲）时，历史实现用
 * {@code stream().min(score)} 恒取候选列表首个，导致突发集中压到同一台。
 * 现改为：收集与最小分同档（容差 {@link #TIE_EPSILON}）的"最闲档"，在其内用
 * 原子计数器轮询分散——既保留"真闲优先"，又避免同分时永远选第一台，
 * 与分片路径 {@code TaskDispatchService.dispatchShard} 的按片轮询语义一致。
 */
@Component
public class LeastLoadStrategy implements RouteStrategy {

    public static final String NAME = "LEAST_LOAD";

    /** CPU 权重 */
    private static final double CPU_WEIGHT = 0.4;
    /** 内存权重 */
    private static final double MEM_WEIGHT = 0.3;
    /** 并发度权重（100 归一化为 0-100） */
    private static final double CONCURRENCY_WEIGHT = 0.3;
    /** 负载相等的判定容差：得分差不超过该值视为同档最闲，在该档内轮询打散 */
    private static final double TIE_EPSILON = 1.0;

    /** 同档最闲 Worker 轮询游标（线程安全，保证多派发线程并发安全） */
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        double min = candidates.stream()
                .mapToDouble(LeastLoadStrategy::score)
                .min()
                .orElse(Double.MAX_VALUE);
        // 收集与最闲者得分同档的候选，避免并发命中同一台；同档内轮询分散
        List<WorkerSnapshot> least = candidates.stream()
                .filter(w -> score(w) <= min + TIE_EPSILON)
                .toList();
        int idx = Math.floorMod(roundRobinIndex.getAndIncrement(), least.size());
        return least.get(idx);
    }

    private static double score(WorkerSnapshot w) {
        double concurrencyRatio = w.maxConcurrent() <= 0 ? 1.0
                : Math.min(1.0, (double) w.runningTasks() / w.maxConcurrent());
        return CPU_WEIGHT * w.cpuUsage()
                + MEM_WEIGHT * w.memUsage()
                + CONCURRENCY_WEIGHT * 100 * concurrencyRatio;
    }
}
