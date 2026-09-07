package com.cronova.scheduler.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SchedulerLockedAspect} 切面单元测试：验证四个核心行为
 * <ul>
 *     <li>抢锁成功：目标方法执行且 finally 释放锁；</li>
 *     <li>抢锁失败：静默跳过（不执行目标方法、不释放他人锁）；</li>
 *     <li>目标方法抛异常：锁仍释放、异常原样上抛；</li>
 *     <li>TTL 自动推导：fixedRate/fixedDelay 周期 × 80%、显式 ttlMillis 优先、cron 兜底默认值。</li>
 * </ul>
 * 纯 Mockito 单测，不依赖 Spring 容器与 Redis。
 */
class SchedulerLockedAspectTest {

    /** 测试用锁 key */
    private static final String LOCK_KEY = "scheduler:lock:ut_aspect";

    /**
     * 测试夹具：承载各种 @Scheduled + @SchedulerLocked 组合的反射目标方法
     */
    static class Fixture {

        /** 数值型 fixedRate 周期任务 */
        @Scheduled(fixedRate = 5000)
        @SchedulerLocked(LOCK_KEY)
        public void fixedRateTask() {
        }

        /** fixedDelay 周期任务 */
        @Scheduled(fixedDelay = 10000)
        @SchedulerLocked(LOCK_KEY)
        public void fixedDelayTask() {
        }

        /** 显式指定 TTL 的任务（优先级高于周期推导） */
        @Scheduled(fixedRate = 5000)
        @SchedulerLocked(value = LOCK_KEY, ttlMillis = 12345)
        public void explicitTtlTask() {
        }

        /** cron 触发型任务（无固定周期，走兜底 TTL） */
        @Scheduled(cron = "0 0 * * * *")
        @SchedulerLocked(LOCK_KEY)
        public void cronTask() {
        }
    }

    /** 被测切面（注入 mock 的锁与环境） */
    private SchedulerLockedAspect aspect;

    /** mock 的分布式锁 */
    private SchedulerLock schedulerLock;

    @BeforeEach
    void setUp() {
        schedulerLock = mock(SchedulerLock.class);
        Environment environment = mock(Environment.class);
        when(environment.resolvePlaceholders(anyString())).thenAnswer(inv -> inv.getArgument(0));
        aspect = new SchedulerLockedAspect(schedulerLock, environment);
    }

    /**
     * 构造指向夹具方法的 mock 切点
     *
     * @param methodName 夹具方法名
     * @return 返回 mock 的 ProceedingJoinPoint（Signature 可解析出真实 Method）
     */
    private ProceedingJoinPoint joinPointOf(String methodName) throws NoSuchMethodException {
        Method method = Fixture.class.getMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getSignature()).thenReturn(signature);
        return pjp;
    }

    @Test
    @DisplayName("抢到锁-目标方法执行且finally释放锁")
    void testProceedWhenLocked() throws Throwable {
        ProceedingJoinPoint pjp = joinPointOf("fixedRateTask");
        when(schedulerLock.tryLock(eq(LOCK_KEY), any())).thenReturn(true);
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp);

        assertEquals("ok", result, "应返回目标方法的返回值");
        verify(pjp).proceed();
        verify(schedulerLock).unlock(LOCK_KEY);
    }

    @Test
    @DisplayName("未抢到锁-静默跳过不执行不释放")
    void testSkipWhenLockNotAcquired() throws Throwable {
        ProceedingJoinPoint pjp = joinPointOf("fixedRateTask");
        when(schedulerLock.tryLock(eq(LOCK_KEY), any())).thenReturn(false);

        Object result = aspect.around(pjp);

        assertNull(result, "未抢到锁应返回 null");
        verify(pjp, never()).proceed();
        verify(schedulerLock, never()).unlock(any());
    }

    @Test
    @DisplayName("目标方法抛异常-锁仍释放且异常上抛")
    void testUnlockWhenProceedThrows() throws Throwable {
        ProceedingJoinPoint pjp = joinPointOf("fixedRateTask");
        when(schedulerLock.tryLock(eq(LOCK_KEY), any())).thenReturn(true);
        when(pjp.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> aspect.around(pjp));
        verify(schedulerLock).unlock(LOCK_KEY);
    }

    @Test
    @DisplayName("TTL推导-fixedRate周期80%")
    void testTtlFromFixedRate() throws Throwable {
        assertTtl("fixedRateTask", Duration.ofMillis(4000)); // 5000 × 0.8
    }

    @Test
    @DisplayName("TTL推导-fixedDelay周期80%")
    void testTtlFromFixedDelay() throws Throwable {
        assertTtl("fixedDelayTask", Duration.ofMillis(8000)); // 10000 × 0.8
    }

    @Test
    @DisplayName("TTL推导-显式ttlMillis优先于周期推导")
    void testExplicitTtlWins() throws Throwable {
        assertTtl("explicitTtlTask", Duration.ofMillis(12345));
    }

    @Test
    @DisplayName("TTL推导-cron任务无周期走兜底默认值")
    void testDefaultTtlForCron() throws Throwable {
        assertTtl("cronTask", Duration.ofMillis(SchedulerLockedAspect.DEFAULT_TTL_MILLIS));
    }

    /**
     * 断言切面传给 tryLock 的 TTL 与期望一致
     *
     * @param fixtureMethod 夹具方法名
     * @param expected      期望的锁 TTL
     */
    private void assertTtl(String fixtureMethod, Duration expected) throws Throwable {
        ProceedingJoinPoint pjp = joinPointOf(fixtureMethod);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        when(schedulerLock.tryLock(eq(LOCK_KEY), ttlCaptor.capture())).thenReturn(false);

        aspect.around(pjp);

        assertEquals(expected, ttlCaptor.getValue(), "锁 TTL 推导结果不符");
    }
}
