package com.sjdt.worker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis 执行权抢占服务（幂等执行兜底，与 worker-Python 的 redis_utils.acquire_run_claim 对齐）
 * <p>
 * MQ 是 at-least-once，可能重投；同一 run 只允许首个 Worker 真正执行一次，
 * 后续重复消息因抢占失败直接 ACK 但不重复跑。
 * <p>
 * 语义要点：
 * <ul>
 *     <li>键名 {@code task:run:claim:{taskRunId}}（与 Python 版 / 调度器约定一致）；</li>
 *     <li>SETNX 抢占，TTL 由调用方传入（约定为任务超时 + 60，覆盖整个执行窗口）；</li>
 *     <li><b>fail-open</b>：Redis 不可用 / 网络抖动时放行执行——丢失幂等但保证可用性，
 *         不因 Redis 故障把任务搞丢（正确性仍有调度器侧状态机条件更新兜底）。</li>
 * </ul>
 * 使用方法：任务执行入口处调用 {@link #acquireRunClaim}，返回 false 时直接 ACK 跳过。
 */
@Slf4j
@Service
public class RunClaimService {

    /** 执行权键前缀（与 worker-Python 对齐） */
    static final String CLAIM_KEY_PREFIX = "task:run:claim:";

    /** Redis 命令超时兜底值（秒）：ttl 非法时使用 */
    private static final long DEFAULT_TTL_SECONDS = 60;

    private final StringRedisTemplate redisTemplate;

    public RunClaimService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 以 SETNX 抢占某个 run 的执行权（幂等执行兜底）
     *
     * @param taskRunId 任务运行实例ID；为空时不抢占直接放行（对齐 Python 版语义）
     * @param workerId  当前 WorkerID（写入 claim value，便于排查归属）
     * @param ttlSeconds 锁 TTL（秒），通常取任务超时 + 60
     * @return true=抢占成功可执行；false=已被其他消息抢占，应跳过
     */
    public boolean acquireRunClaim(String taskRunId, String workerId, long ttlSeconds) {
        if (taskRunId == null || taskRunId.isBlank()) {
            return true;
        }
        long ttl = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
        try {
            Boolean claimed = redisTemplate.opsForValue().setIfAbsent(
                    CLAIM_KEY_PREFIX + taskRunId, workerId, Duration.ofSeconds(ttl));
            return Boolean.TRUE.equals(claimed);
        } catch (Exception e) {
            // Redis 不可用 / 网络抖动：fail-open，允许执行，避免把任务搞丢
            log.warn("执行权抢占失败，fail-open 放行执行，runId={}", taskRunId, e);
            return true;
        }
    }
}
