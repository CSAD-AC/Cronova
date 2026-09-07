package com.cronova.scheduler.common;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调度实例自注册表（集群视图）
 * <p>
 * 每个调度器实例启动后向 Redis ZSet {@code scheduler:instances} 注册自己：
 * Member=实例ID（hostname:短UUID），Score=最近心跳时间戳（epochMillis）。
 * Dashboard / 运维接口通过 {@link #onlineInstances()} 获取"当前有几个调度实例在线"，
 * 作为多实例高可用改造（锁 + CAS）的直观证明。
 * <p>
 * <b>为什么不用 @Scheduled + @SchedulerLocked</b>：分布式锁解决的是"集群内只有一个实例执行"
 * （扫描、超时监控等集群级定时任务）；而实例注册是<b>实例级</b>行为——每个实例都必须注册自己，
 * 加锁反而会丢实例。因此用自管理心跳线程实现，避免误用集群单实例语义，
 * 也不会触发 ScheduledLockCheck 的"漏加锁"启动告警。
 * <p>
 * 使用方法：无需手动调用，Spring 容器启动后自动注册心跳；查询在线实例调用
 * {@link #onlineInstances()} / {@link #onlineCount()}。
 */
@Slf4j
@Component
public class SchedulerInstanceRegistry {

    /** 心跳间隔：每 10s 刷新一次自己的 score */
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;

    /** 在线判定窗口：30s 内有心跳视为在线（容忍 2 个心跳周期的网络抖动） */
    private static final long ONLINE_WINDOW_SECONDS = 30;

    /** 死成员清理阈值：心跳超过 5 分钟的成员从 ZSet 移除（避免长期膨胀） */
    private static final long STALE_MEMBER_SECONDS = 300;

    /** Redis 操作模板（ZSet 读写） */
    private final RedisTemplate<String, String> redisTemplate;

    /** 本实例唯一标识（hostname:短UUID，进程生命周期内不变） */
    private final String instanceId;

    /** 自管理心跳线程（单线程守护，应用关闭时释放） */
    private ScheduledExecutorService heartbeatExecutor;

    @Autowired
    public SchedulerInstanceRegistry(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.instanceId = resolveInstanceId();
    }

    /**
     * 启动即注册并开启周期心跳；顺手清理 5 分钟无心跳的死成员。
     */
    @PostConstruct
    public void start() {
        heartbeat();
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scheduler-instance-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatExecutor.scheduleWithFixedDelay(this::heartbeat,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("调度实例已注册，instanceId: {}，心跳间隔: {}s", instanceId, HEARTBEAT_INTERVAL_SECONDS);
    }

    /**
     * 优雅下线：从注册表移除自己（kill -9 场景由 5 分钟死成员清理兜底）。
     */
    @PreDestroy
    public void shutdown() {
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
        }
        try {
            redisTemplate.opsForZSet().remove(RedisKey.SCHEDULER_INSTANCES, instanceId);
            log.info("调度实例已注销，instanceId: {}", instanceId);
        } catch (Exception e) {
            log.warn("调度实例注销失败（忽略，等待死成员清理），instanceId: {}", instanceId, e);
        }
    }

    /**
     * 查询当前在线的调度实例列表（30s 内有心跳）。
     *
     * @return 在线实例信息（按最近心跳降序）
     */
    public List<InstanceInfo> onlineInstances() {
        long minScore = System.currentTimeMillis() - ONLINE_WINDOW_SECONDS * 1000;
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeByScoreWithScores(
                        RedisKey.SCHEDULER_INSTANCES, minScore, Long.MAX_VALUE);
        List<InstanceInfo> result = new ArrayList<>();
        if (tuples == null) {
            return result;
        }
        for (var tuple : tuples) {
            if (tuple.getValue() == null) {
                continue;
            }
            long lastHeartbeatMs = tuple.getScore() != null ? tuple.getScore().longValue() : 0L;
            result.add(new InstanceInfo(tuple.getValue(),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(lastHeartbeatMs),
                            java.time.ZoneId.systemDefault())));
        }
        return result;
    }

    /**
     * 查询当前在线调度实例数量。
     *
     * @return 在线实例数
     */
    public int onlineCount() {
        return onlineInstances().size();
    }

    /**
     * 获取本实例标识。
     *
     * @return 实例ID（hostname:短UUID）
     */
    public String selfInstanceId() {
        return instanceId;
    }

    /**
     * 心跳一次：ZADD 刷新自己的 score，并清理超时死成员。
     * Redis 异常仅告警不抛出——注册表不可用不影响调度主流程（观测性数据可短暂缺失）。
     */
    private void heartbeat() {
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForZSet().add(RedisKey.SCHEDULER_INSTANCES, instanceId, now);
            redisTemplate.opsForZSet().removeRangeByScore(RedisKey.SCHEDULER_INSTANCES,
                    0, now - STALE_MEMBER_SECONDS * 1000);
        } catch (Exception e) {
            log.warn("调度实例心跳失败（Redis 不可达，注册数据短暂过期），instanceId: {}", instanceId, e);
        }
    }

    /**
     * 生成实例标识：hostname + 短 UUID（hostname 便于人读，UUID 防同名主机冲突）。
     *
     * @return 实例ID
     */
    private static String resolveInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        return host + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 在线调度实例信息（对外展示用）。
     */
    public record InstanceInfo(String instanceId, LocalDateTime lastHeartbeat) {
    }
}
