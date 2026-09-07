package com.sjdt.scheduler.task;

import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.common.SchedulerLocked;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务调度触发器
 * <p>
 * <ul>
 *     <li>扫描周期 5s，触发精度达到秒级；</li>
 *     <li>@SchedulerLocked 分布式锁：只有抢到锁的实例真正扫描，多实例部署不重复触发；
 *         锁 TTL 由切面按周期自动推导（5s × 80% = 4s），无需手工维护。</li>
 * </ul>
 * 宁可偶尔双扫也不能因死锁永久停摆，正确性由 {@code CronTaskScheduler.trigger()} 的数据库 CAS 兜底。
 */
@Slf4j
@Component
public class ScheduledTaskRunnerTask {

    /** 扫描周期（毫秒） */
    private static final long SCAN_PERIOD_MS = 5000;

    @Autowired
    private CronTaskScheduler cronTaskScheduler;

    /**
     * 每5秒扫描并触发到期的定时任务（仅持锁实例执行）
     */
    @Scheduled(fixedRate = SCAN_PERIOD_MS)
    @SchedulerLocked(RedisKey.SCHEDULER_LOCK_SCAN)
    public void runCronTaskScheduler() {
        try {
            cronTaskScheduler.scanAndTrigger();
        } catch (Exception e) {
            log.error("定时任务调度器执行异常", e);
        }
    }
}
