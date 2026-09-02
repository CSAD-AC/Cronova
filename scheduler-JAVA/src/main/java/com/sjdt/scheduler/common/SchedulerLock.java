package com.sjdt.scheduler.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis SETNX + Lua 校验释放的分布式锁
 * <p>
 * 用途：调度集群中"每轮只有一个实例真正扫描"，避免多实例空转竞争。
 * <p>
 * 两层防重的分工：
 * <ul>
 *     <li><b>锁解决吞吐</b>：SET NX EX 保证同一时刻只有一个实例抢到锁，减少无谓竞争；</li>
 *     <li><b>正确性由数据库 CAS 兜底</b>：即使锁过期失效、双实例同时扫描同一行，
 *         next_trigger_time 的乐观锁条件也只允许一个 UPDATE 成功。</li>
 * </ul>
 * 关键实现点：
 * <ul>
 *     <li>value 放实例 token（UUID）+ Lua 校验后删除：防止实例 A 锁超时易主后，A 的 finally 误删 B 的锁；</li>
 *     <li>TTL 由调用方传入，调度场景约定「略小于扫描周期」——宁可偶尔双扫，也不因死锁永久停摆。</li>
 * </ul>
 */
@Slf4j
@Component
public class SchedulerLock {

    private static final String LOCK_KEY = RedisKey.SCHEDULER_LOCK_SCAN;

    /** 释放锁的 Lua 脚本：仅当持有者仍是本实例 token 时才删除 */
    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else return 0 end""";

    /** 实例级标识：本实例每次获取锁写入同一个 token，释放时用于校验归属 */
    private final String token = UUID.randomUUID().toString();

    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public SchedulerLock(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    }

    /**
     * 尝试获取锁（SET key token NX EX ttl）
     *
     * @param ttl 锁自动过期时间；建议略小于调用方的扫描周期
     * @return true 表示本实例获得锁
     */
    public boolean tryLock(Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, ttl));
        } catch (Exception e) {
            log.error("获取调度锁异常", e);
            return false; // 锁不可用时降级为"本轮不扫描"，避免并发触发；正确性由 CAS 兜底
        }
    }

    /**
     * 释放锁（Lua 校验 token 后删除，防止误删其他实例持有的锁）
     */
    public void unlock() {
        try {
            redisTemplate.execute(unlockScript, List.of(LOCK_KEY), token);
        } catch (Exception e) {
            log.error("释放调度锁异常", e);
        }
    }
}
