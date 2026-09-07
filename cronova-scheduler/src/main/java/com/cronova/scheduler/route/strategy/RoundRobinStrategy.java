package com.cronova.scheduler.route.strategy;

import com.cronova.scheduler.common.RedisKey;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.route.WorkerSnapshot;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 轮询策略：任务在候选 Worker 间轮流分配
 * <p>
 * 游标放 Redis（INCR），多调度实例共享同一计数器，避免单机内存计数无法跨实例。
 */
@Component
public class RoundRobinStrategy implements RouteStrategy {

    public static final String NAME = "ROUND_ROBIN";

    private final RedisTemplate<String, Object> redisTemplate;

    public RoundRobinStrategy(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        long idx = Math.floorMod(redisTemplate.opsForValue().increment(RedisKey.ROUTE_RR), candidates.size());
        return candidates.get((int) idx);
    }
}
