package com.cronova.scheduler.route.strategy;

import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.route.WorkerSnapshot;

import java.util.List;

/**
 * 路由策略 SPI：从候选 Worker 中选出一个执行者
 * <p>
 * 新增策略只需实现本接口并注册为 Spring Bean，RouteStrategyRouter 自动装配。
 * name() 必须与 tasks.route_strategy 列存储值一致（如 "ROUND_ROBIN"）。
 */
public interface RouteStrategy {

    String name();

    /**
     * 从候选 Worker 中选择一个
     *
     * @param task       任务（策略可读取 route_strategy 之外的信息，如 taskId 用于一致性哈希）
     * @param candidates 候选 Worker（已在线、已按 requiredTags 过滤）
     * @return 选中的 Worker；无法选出时返回 null（上层 park 兜底）
     */
    WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates);
}
