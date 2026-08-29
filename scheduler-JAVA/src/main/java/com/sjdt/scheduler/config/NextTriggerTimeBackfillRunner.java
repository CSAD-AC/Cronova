package com.sjdt.scheduler.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.util.CronNextTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存量 cron 任务 next_trigger_time 回填器
 * <p>
 * 启动时对 {@code next_trigger_time IS NULL} 的活跃 cron 任务，用 Cron 表达式计算下一次触发时间补齐，
 * 使存量任务首轮就能被"到期驱动扫描"命中。
 * <p>
 * 幂等：只处理 {@code next_trigger_time IS NULL} 的行，且 UPDATE 同样带 IS NULL 条件，
 * 可重复启动、多实例并发启动也不会覆盖已回填的值。
 */
@Slf4j
@Component
public class NextTriggerTimeBackfillRunner implements ApplicationRunner {

    private final TaskMapper taskMapper;

    @Autowired
    public NextTriggerTimeBackfillRunner(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Task> toBackfill = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                    .eq(Task::getStatus, TaskStatus.active)
                    .eq(Task::getTaskType, TaskType.cron)
                    .isNull(Task::getNextTriggerTime));

            if (toBackfill.isEmpty()) {
                log.info("next_trigger_time 回填：无存量活跃 cron 任务需要回填");
                return;
            }

            int success = 0;
            for (Task task : toBackfill) {
                try {
                    LocalDateTime next = CronNextTimeUtil.next(LocalDateTime.now(), task.getCronExpr());
                    int updated = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                            .eq(Task::getId, task.getId())
                            .isNull(Task::getNextTriggerTime) // 幂等：并发/重复启动不覆盖已回填值
                            .set(Task::getNextTriggerTime, next));
                    if (updated > 0) {
                        success++;
                    }
                } catch (Exception e) {
                    log.error("回填任务{}的next_trigger_time失败，cron: {}", task.getId(), task.getCronExpr(), e);
                }
            }
            log.info("next_trigger_time 回填完成：共{}个任务，成功{}个", toBackfill.size(), success);
        } catch (Exception e) {
            log.error("next_trigger_time 回填异常", e);
        }
    }
}
