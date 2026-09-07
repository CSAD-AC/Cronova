package com.cronova.scheduler.common;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * {@link SchedulerLocked} 注解的 AOP 切面
 * <p>
 * 拦截所有标注 {@code @SchedulerLocked} 的定时任务方法，统一实现：
 * <ol>
 *     <li>方法执行前按注解 key 抢 Redis 分布式锁，未抢到则<b>静默跳过本轮</b>（其他实例正在执行）；</li>
 *     <li>方法执行后在 finally 中释放锁，<b>业务代码无需手写 tryLock/unlock 样板</b>；</li>
 *     <li>锁 TTL 自动推导：读同方法上的 {@code @Scheduled} 周期 × 80%，显式 ttlMillis 优先；</li>
 *     <li>统一观测埋点：抢锁失败、执行耗时均在本切面输出日志（后续可观测性阶段的天然收口点）。</li>
 * </ol>
 * 原理说明：Spring 的 {@code ScheduledAnnotationBeanPostProcessor} 注册的是<b>代理后</b>的 bean 方法，
 * 因此切面对定时任务方法生效（与 ShedLock 同机制）。
 * <p>
 * 使用方法：见 {@link SchedulerLocked} 注解文档。
 */
@Slf4j
@Aspect
@Component
public class SchedulerLockedAspect {

    /** TTL 自动推导比例：周期 × 80%，留 20% 余量防止持锁执行略超时后锁已易主造成双扫 */
    static final double TTL_RATIO = 0.8;

    /** 无法从 @Scheduled 推导周期时（如 cron 触发型）的兜底 TTL（毫秒） */
    static final long DEFAULT_TTL_MILLIS = 30_000;

    /** 分布式锁底层实现（token + Lua 校验释放） */
    private final SchedulerLock schedulerLock;

    /** 用于解析 @Scheduled 周期占位符（fixedRateString = "${...}" 形式） */
    private final Environment environment;

    public SchedulerLockedAspect(SchedulerLock schedulerLock, Environment environment) {
        this.schedulerLock = schedulerLock;
        this.environment = environment;
    }

    /**
     * 环绕拦截 @SchedulerLocked 方法：抢锁才执行，执行完 finally 释放
     *
     * @param pjp 切点（当前仅支持方法级拦截）
     * @return 目标方法返回值；未抢到锁时返回 null（定时任务方法均无返回值，跳过即不执行）
     * @throws Throwable 目标方法抛出的原始异常（原样上抛给调度线程，不吞异常）
     */
    @Around("@annotation(com.cronova.scheduler.common.SchedulerLocked)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        SchedulerLocked annotation = method.getAnnotation(SchedulerLocked.class);
        String lockKey = annotation.value();
        long ttlMillis = resolveTtlMillis(method, annotation);

        if (!schedulerLock.tryLock(lockKey, Duration.ofMillis(ttlMillis))) {
            log.debug("调度锁被其他实例持有，跳过本轮 | key={}, method={}", lockKey, method.getName());
            return null;
        }

        long startMillis = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            schedulerLock.unlock(lockKey);
            log.debug("定时任务执行完成 | key={}, method={}, 耗时={}ms",
                    lockKey, method.getName(), System.currentTimeMillis() - startMillis);
        }
    }

    /**
     * 解析锁 TTL（毫秒）：显式 ttlMillis 优先，否则按 @Scheduled 周期 × 80% 推导，最后兜底默认值
     *
     * @param method     定时任务方法（同时挂有 @Scheduled 与 @SchedulerLocked）
     * @param annotation 锁注解实例
     * @return 锁自动过期时间（毫秒）
     */
    private long resolveTtlMillis(Method method, SchedulerLocked annotation) {
        if (annotation.ttlMillis() > 0) {
            return annotation.ttlMillis();
        }
        long periodMillis = resolvePeriodMillis(method);
        if (periodMillis > 0) {
            return Math.round(periodMillis * TTL_RATIO);
        }
        log.debug("无法从 @Scheduled 推导周期，使用兜底 TTL {}ms | method={}", DEFAULT_TTL_MILLIS, method.getName());
        return DEFAULT_TTL_MILLIS;
    }

    /**
     * 从方法上的 @Scheduled 注解解析扫描周期（毫秒）
     * <p>
     * 依次尝试 fixedRate / fixedDelay 数值形式，再尝试字符串形式
     * （可能含 "${...}" 占位符，经 Environment 解析后按数值解析）。
     *
     * @param method 定时任务方法
     * @return 周期毫秒数；解析不到返回 -1
     */
    private long resolvePeriodMillis(Method method) {
        Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(method, Scheduled.class);
        if (scheduled == null) {
            return -1;
        }
        if (scheduled.fixedRate() > 0) {
            return scheduled.fixedRate();
        }
        if (scheduled.fixedDelay() > 0) {
            return scheduled.fixedDelay();
        }
        String periodStr = !scheduled.fixedRateString().isEmpty() ? scheduled.fixedRateString()
                : !scheduled.fixedDelayString().isEmpty() ? scheduled.fixedDelayString()
                : null;
        if (periodStr == null) {
            return -1;
        }
        try {
            return Long.parseLong(environment.resolvePlaceholders(periodStr));
        } catch (NumberFormatException e) {
            log.debug("无法解析 @Scheduled 周期字符串 '{}' | method={}", periodStr, method.getName());
            return -1;
        }
    }
}
