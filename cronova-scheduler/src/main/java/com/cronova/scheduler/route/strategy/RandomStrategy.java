package com.cronova.scheduler.route.strategy;

import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机策略：从候选 Worker 中随机选一个（无状态，实现最简单）
 */
@Component
public class RandomStrategy implements RouteStrategy {

    public static final String NAME = "RANDOM";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
