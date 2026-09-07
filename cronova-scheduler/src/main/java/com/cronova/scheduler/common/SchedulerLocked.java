package com.cronova.scheduler.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务分布式锁注解
 * <p>
 * 标注在 {@code @Scheduled} 方法上，由 {@link SchedulerLockedAspect} 切面统一实现
 * "抢锁才执行、执行完释放"，业务方法内<b>不再手写 tryLock/try-finally/unlock 样板</b>。
 * <p>
 * 使用方式：
 * <pre>{@code
 * @Scheduled(fixedRate = 30000)
 * @SchedulerLocked(RedisKey.SCHEDULER_LOCK_TIMEOUT)
 * public void checkTimeoutTasks() { ... }
 * }</pre>
 * <p>
 * 锁 TTL 推导优先级：
 * <ol>
 *     <li>显式指定 {@link #ttlMillis()}；</li>
 *     <li>按 {@code @Scheduled} 的 fixedRate/fixedDelay 周期 × 80% 自动推导
 *         （约定「TTL 略小于扫描周期」——宁可在实例崩溃后短暂停摆等待锁过期，
 *         也不因持锁执行超时导致下一轮被跳过）；</li>
 *     <li>都取不到时兜底 30s（如 cron 触发型任务无固定周期）。</li>
 * </ol>
 * 正确性不依赖本锁：即使锁失效导致双实例同扫，状态迁移由数据库 CAS / 条件更新兜底，
 * 锁仅用于消除多实例重复扫描的吞吐浪费。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SchedulerLocked {

    /**
     * 分布式锁的 Redis key
     * <p>
     * 不同职责的定时任务必须使用不同 key（取 {@link RedisKey} 中的 SCHEDULER_LOCK_* 常量），
     * 避免互相争抢同一把锁导致部分任务被饿死。
     */
    String value();

    /**
     * 锁自动过期时间（毫秒），默认 -1 表示不指定
     * <p>
     * 不指定时由切面按 {@code @Scheduled} 周期 × 80% 自动推导；显式指定则优先使用。
     */
    long ttlMillis() default -1;
}
