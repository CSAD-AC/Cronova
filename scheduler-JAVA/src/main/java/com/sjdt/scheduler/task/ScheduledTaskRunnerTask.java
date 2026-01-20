package com.sjdt.scheduler.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务调度触发器
 * 定期调用CronTaskScheduler扫描和执行到期的定时任务
 */
@Slf4j
@Component
public class ScheduledTaskRunnerTask {

    @Autowired
    private CronTaskScheduler cronTaskScheduler;

    /**
     * 每60秒扫描并执行到期的定时任务
     */
    @Scheduled(fixedRate = 60000)
    public void runCronTaskScheduler() {
        log.debug("定时任务调度触发器开始执行");
        try {
            cronTaskScheduler.scanAndExecuteTasks();
        } catch (Exception e) {
            log.error("定时任务调度器执行异常", e);
        }
    }
}
