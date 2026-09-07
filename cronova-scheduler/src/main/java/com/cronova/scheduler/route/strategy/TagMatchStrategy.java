package com.cronova.scheduler.route.strategy;

import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 标签匹配策略：从已满足任务 requiredTags 的候选 Worker 中按最少负载选择
 * <p>
 * 标签过滤在派发入口统一完成（无论何种策略，requiredTags 都生效）；
 * 本策略在过滤后的候选集上退化为最少负载选择，保证配置为 TAG_MATCH 时语义正确。
 */
@Component
public class TagMatchStrategy implements RouteStrategy {

    public static final String NAME = "TAG_MATCH";

    private final LeastLoadStrategy leastLoadStrategy;

    public TagMatchStrategy(LeastLoadStrategy leastLoadStrategy) {
        this.leastLoadStrategy = leastLoadStrategy;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        return leastLoadStrategy.select(task, candidates);
    }
}
