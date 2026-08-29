package com.sjdt.scheduler.task;

import com.sjdt.scheduler.common.SchedulerLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 定时任务调度触发器
 * <p>
 * <ul>
 *     <li>扫描周期 5s，触发精度达到秒级；</li>
 *     <li>每轮先抢 Redis 分布式锁：只有抢到锁的实例真正扫描，多实例部署不重复触发。</li>
 * </ul>
 * 锁 TTL(4s) 略小于扫描周期(5s)：宁可偶尔双扫也不能因死锁永久停摆，
 * 正确性由 {@code CronTaskScheduler.trigger()} 的数据库 CAS 兜底。
 */
@Slf4j
@Component
public class ScheduledTaskRunnerTask {

    /** 扫描周期（毫秒） */
    private static final long SCAN_PERIOD_MS = 5000;

    /** 扫描锁 TTL：略小于扫描周期，防止死锁停摆 */
    private static final Duration SCAN_LOCK_TTL = Duration.ofSeconds(4);

    @Autowired
    private CronTaskScheduler cronTaskScheduler;

    @Autowired
    private SchedulerLock schedulerLock;

    /**
     * 每5秒扫描并触发到期的定时任务（仅持锁实例执行）
     */
    @Scheduled(fixedRate = SCAN_PERIOD_MS)
    public void runCronTaskScheduler() {
        if (!schedulerLock.tryLock(SCAN_LOCK_TTL)) {
            // 其他实例已持锁，本轮跳过（分布式锁解决吞吐，正确性由 CAS 兜底）
            return;
        }
        try {
            cronTaskScheduler.scanAndTrigger();
        } catch (Exception e) {
            log.error("定时任务调度器执行异常", e);
        } finally {
            schedulerLock.unlock();
        }
    }
}
