package com.cronova.scheduler.route.strategy;

import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 故障转移策略：候选已在线，按 lastHeartbeat 由新到旧取最近心跳的 Worker；
 * 候选集为空时返回 null，由上层 park 兜底。
 */
@Component
public class FailoverStrategy implements RouteStrategy {

    public static final String NAME = "FAILOVER";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        return candidates.stream()
                .max(Comparator.comparingLong(WorkerSnapshot::lastHeartbeatEpochMs))
                .orElse(null);
    }
}
