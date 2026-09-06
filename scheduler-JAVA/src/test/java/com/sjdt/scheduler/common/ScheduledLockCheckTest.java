package com.sjdt.scheduler.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ScheduledLockCheck} 启动期校验器单元测试：验证注解配套检测的四种情形
 * <ul>
 *     <li>注解配套齐全（@Scheduled + @SchedulerLocked）：无违规；</li>
 *     <li>@Scheduled 缺 @SchedulerLocked：报一条违规（多实例并发重复执行隐患）；</li>
 *     <li>@SchedulerLocked 缺 @Scheduled：报一条违规（注解无效）；</li>
 *     <li>普通 bean：无违规。</li>
 * </ul>
 * 纯单测，不依赖 Spring 容器。
 */
class ScheduledLockCheckTest {

    /** 被测校验器（beanFactory 参数仅容器启动时使用，单测传 null） */
    private final ScheduledLockCheck check = new ScheduledLockCheck(null);

    /** 夹具：注解配套齐全的正确姿势 */
    static class LockedFixture {
        @Scheduled(fixedRate = 5000)
        @SchedulerLocked(RedisKey.SCHEDULER_LOCK_SCAN)
        public void task() {
        }
    }

    /** 夹具：@Scheduled 忘加锁（校验器要抓的典型遗漏） */
    static class MissingLockFixture {
        @Scheduled(fixedRate = 5000)
        public void task() {
        }
    }

    /** 夹具：锁注解误加到非 @Scheduled 方法 */
    static class OrphanLockFixture {
        @SchedulerLocked(RedisKey.SCHEDULER_LOCK_SCAN)
        public void notScheduled() {
        }
    }

    /** 夹具：与定时任务无关的普通 bean */
    static class PlainFixture {
        public void plain() {
        }
    }

    @Test
    @DisplayName("注解配套齐全-无违规")
    void testLockedBeanPasses() {
        List<String> violations = check.checkBean("lockedFixture", LockedFixture.class);
        assertTrue(violations.isEmpty(), "配套齐全不应有违规");
    }

    @Test
    @DisplayName("@Scheduled缺@SchedulerLocked-报一条违规")
    void testMissingLockReported() {
        List<String> violations = check.checkBean("missingLockFixture", MissingLockFixture.class);
        assertEquals(1, violations.size(), "缺锁应报一条违规");
        assertTrue(violations.get(0).contains("missingLockFixture"), "违规信息应包含 bean 名便于定位");
    }

    @Test
    @DisplayName("@SchedulerLocked缺@Scheduled-报一条违规")
    void testOrphanLockReported() {
        List<String> violations = check.checkBean("orphanLockFixture", OrphanLockFixture.class);
        assertEquals(1, violations.size(), "孤立锁注解应报一条违规");
    }

    @Test
    @DisplayName("普通bean-无违规")
    void testPlainBeanPasses() {
        List<String> violations = check.checkBean("plainFixture", PlainFixture.class);
        assertTrue(violations.isEmpty(), "普通 bean 不应有违规");
    }
}
