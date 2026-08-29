package com.sjdt.scheduler.route.strategy;

import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.route.WorkerSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由策略注册中心：按 name() 查策略并执行选择
 * <p>
 * 自动收集所有 RouteStrategy 实现；未配置策略或策略名未知时回退 LEAST_LOAD。
 */
@Slf4j
@Component
public class RouteStrategyRouter {

    public static final String DEFAULT_STRATEGY = "LEAST_LOAD";

    private final Map<String, RouteStrategy> strategies = new HashMap<>();

    @Autowired
    public RouteStrategyRouter(List<RouteStrategy> all) {
        for (RouteStrategy strategy : all) {
            if (strategies.put(strategy.name(), strategy) != null) {
                log.warn("路由策略名重复: {}", strategy.name());
            }
        }
        log.info("已加载路由策略: {}", strategies.keySet());
    }

    /**
     * 按任务配置的路由策略选出 Worker
     */
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        RouteStrategy strategy = strategies.getOrDefault(resolveName(task), strategies.get(DEFAULT_STRATEGY));
        if (strategy == null) {
            log.error("无可用的路由策略（含默认策略），任务: {}", task.getId());
            return null;
        }
        WorkerSnapshot selected = strategy.select(task, candidates);
        log.debug("路由策略 {} 为任务{}选择 Worker: {}",
                strategy.name(), task.getId(), selected == null ? "null" : selected.id());
        return selected;
    }

    private String resolveName(Task task) {
        String name = task.getRouteStrategy();
        if (name == null || name.isBlank()) {
            return DEFAULT_STRATEGY;
        }
        return name.trim().toUpperCase();
    }
}
