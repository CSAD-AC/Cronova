package com.sjdt.scheduler.route.strategy;

import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 最少负载策略：按真实负载打分选最闲的 Worker
 * <p>
 * score = 0.4*cpu + 0.3*mem + 0.3*100*running/maxConcurrent，取最小。
 * 相比 xxl-job 的 LFU（只看触发频次），本策略更贴近节点真实负载；
 * 权重系数集中为常量，便于调参。
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

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        return candidates.stream()
                .min(Comparator.comparingDouble(LeastLoadStrategy::score))
                .orElse(null);
    }

    private static double score(WorkerSnapshot w) {
        double concurrencyRatio = w.maxConcurrent() <= 0 ? 1.0
                : Math.min(1.0, (double) w.runningTasks() / w.maxConcurrent());
        return CPU_WEIGHT * w.cpuUsage()
                + MEM_WEIGHT * w.memUsage()
                + CONCURRENCY_WEIGHT * 100 * concurrencyRatio;
    }
}
