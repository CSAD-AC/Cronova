package com.sjdt.scheduler.route.strategy;

import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 故障转移策略：按心跳新鲜度排序，优先把任务交给最近心跳的 Worker
 * <p>
 * 候选集本身已经在线（心跳键存活）；这里按 lastHeartbeat 由新到旧排序取第一个，
 * 心跳越新代表刚活动过，可用性越高。若候选集为空则返回 null，由上层 park 兜底。
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
