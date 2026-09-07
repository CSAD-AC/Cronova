package com.cronova.scheduler.route;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.common.RedisKey;
import com.cronova.scheduler.domain.enums.WorkerStatus;
import com.cronova.scheduler.domain.po.Tag;
import com.cronova.scheduler.domain.po.Worker;
import com.cronova.scheduler.domain.po.WorkerTag;
import com.cronova.scheduler.mapper.TagMapper;
import com.cronova.scheduler.mapper.WorkerMapper;
import com.cronova.scheduler.mapper.WorkerTagMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Worker 在线快照注册表：为路由提供"当前可用 Worker + 实时负载 + 标签"的统一视图
 * <p>
 * 在线判定：数据库 status=online（基础集）+ Redis 心跳键存活（实时二次确认，TTL 30s）。
 * 负载与最后心跳来自 Redis 信息 Hash（Worker 每 10s 上报）。
 * 标签来自 worker_tags + tags 表（每轮一次批量加载，避免逐 Worker 查询）。
 */
@Slf4j
@Component
public class WorkerRegistry {

    private final WorkerMapper workerMapper;
    private final WorkerTagMapper workerTagMapper;
    private final TagMapper tagMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public WorkerRegistry(WorkerMapper workerMapper,
                          WorkerTagMapper workerTagMapper,
                          TagMapper tagMapper,
                          RedisTemplate<String, Object> redisTemplate) {
        this.workerMapper = workerMapper;
        this.workerTagMapper = workerTagMapper;
        this.tagMapper = tagMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 在线 Worker 快照列表（DB 在线 + Redis 心跳存活 + Redis 负载）
     */
    public List<WorkerSnapshot> onlineWorkers() {
        List<Worker> dbOnline = workerMapper.selectList(new LambdaQueryWrapper<Worker>()
                .eq(Worker::getStatus, WorkerStatus.online));
        if (dbOnline.isEmpty()) {
            return List.of();
        }

        Map<String, Set<String>> tagsByWorker = loadTagsByWorker();
        List<WorkerSnapshot> snapshots = new ArrayList<>(dbOnline.size());
        for (Worker worker : dbOnline) {
            if (!isHeartbeatAlive(worker.getId())) {
                continue; // Redis 心跳键已过期（更实时），跳过；DB 的 30s 同步随后会标记离线
            }
            Map<Object, Object> info = loadInfo(worker.getId());
            snapshots.add(WorkerSnapshot.from(worker, tagsByWorker.getOrDefault(worker.getId(), Set.of()), info));
        }
        log.debug("在线Worker快照: {} 个", snapshots.size());
        return snapshots;
    }

    /**
     * 按标签过滤：Worker 必须拥有所有要求的标签
     *
     * @param workers 候选快照
     * @param required 任务需要的标签集合；为空/ null 时不过滤
     */
    public List<WorkerSnapshot> filterByTags(List<WorkerSnapshot> workers, Set<String> required) {
        if (required == null || required.isEmpty()) {
            return workers;
        }
        return workers.stream()
                .filter(w -> w.tags() != null && w.tags().containsAll(required))
                .collect(Collectors.toList());
    }

    /**
     * 判断某 Worker 是否存活（Redis 心跳键是否存在）
     */
    public boolean isOnline(String workerId) {
        try {
            return redisTemplate.hasKey(RedisKey.getWorkerHeartbeatKey(workerId));
        } catch (Exception e) {
            log.error("查询Worker心跳状态失败，WorkerID: {}", workerId, e);
            return false;
        }
    }

    /**
     * 批量加载 workerId -> 标签名集合（每轮一次，避免 N+1）
     */
    private Map<String, Set<String>> loadTagsByWorker() {
        Map<String, Set<String>> result = new HashMap<>();
        try {
            Map<Integer, String> tagNames = tagMapper.selectList(null).stream()
                    .collect(Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
            List<WorkerTag> allWorkerTags = workerTagMapper.selectList(null);
            for (WorkerTag wt : allWorkerTags) {
                String name = tagNames.get(wt.getTagId());
                if (name == null) {
                    continue;
                }
                result.computeIfAbsent(wt.getWorkerId(), k -> new HashSet<>()).add(name);
            }
        } catch (Exception e) {
            log.error("加载Worker标签失败，路由将跳过标签过滤", e);
        }
        return result;
    }

    private Map<Object, Object> loadInfo(String workerId) {
        try {
            return redisTemplate.opsForHash().entries(RedisKey.getWorkerInfoKey(workerId));
        } catch (Exception e) {
            log.error("读取Worker负载信息失败，WorkerID: {}", workerId, e);
            return Map.of();
        }
    }

    private boolean isHeartbeatAlive(String workerId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKey.getWorkerHeartbeatKey(workerId)));
        } catch (Exception e) {
            log.error("查询Worker心跳状态失败，WorkerID: {}", workerId, e);
            return false;
        }
    }
}
