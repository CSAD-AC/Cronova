package com.cronova.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cronova.scheduler.common.RedisKey;
import com.cronova.scheduler.common.SchedulerLocked;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SERIAL 排队推进器
 * <p>
 * 阻塞策略 SERIAL 下产生的 <code>queued</code> run 不会自行派发，需要在上一个兄弟 run
 * 进入终态后由本组件推进。推进逻辑：
 * <ol>
 *     <li>若该任务已无任何 <code>running/pending</code> 兄弟（上一轮已结束）；</li>
 *     <li>则把队首 run 条件更新为 <code>pending</code> 并派发执行。</li>
 * </ol>
 * 条件更新的状态条件（queued → pending）保证同一 run 只会被推进一次，且只有"队首"能被推进，
 * 实现严格串行：前一个 success 之后才派发下一个，无并行。
 * <p>
 * <b>数据驱动</b>：守卫扫描（5s）先查询 DB 是否存在 queued；仅当存在排队 run 时才进一步
 * 推进，平时（无排队任务）几乎零轮询成本。
 * <p>
 * <b>分布式锁</b>：@SchedulerLocked 用 Redis 锁（{@link RedisKey#SCHEDULER_LOCK_QUEUED}）串行化
 * 多实例推进，避免两个实例对同一排队 run 重复派发（条件更新本身幂等，锁进一步消除竞争与重复发送）。
 */
@Slf4j
@Component
public class QueuedRunDispatcher {

    /** 守卫扫描周期（毫秒）：仅在存在排队 run 时才实际推进，低频即足够 */
    private static final long SCAN_PERIOD_MS = 5000;

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;

    /** 定时调度器（复用其派发入口） */
    private final CronTaskScheduler cronTaskScheduler;

    @Autowired
    public QueuedRunDispatcher(TaskRunMapper taskRunMapper,
                               CronTaskScheduler cronTaskScheduler) {
        this.taskRunMapper = taskRunMapper;
        this.cronTaskScheduler = cronTaskScheduler;
    }

    /**
     * 低频守卫扫描排队 run：仅当存在 queued 记录时才推进（数据驱动）
     */
    @Scheduled(fixedRate = SCAN_PERIOD_MS)
    @SchedulerLocked(RedisKey.SCHEDULER_LOCK_QUEUED)
    public void scan() {
        // 只查是否存在排队 run；无则整轮零成本返回
        List<TaskRun> queuedRuns = taskRunMapper.selectList(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getStatus, TaskRunStatus.queued));

        if (queuedRuns.isEmpty()) {
            return;
        }
        log.debug("扫描到{}个排队中的run", queuedRuns.size());

        for (TaskRun queued : queuedRuns) {
            try {
                if (!canPromote(queued)) {
                    continue;
                }
                if (promoteToPending(queued.getId())) {
                    cronTaskScheduler.dispatchQueuedRun(queued.getId());
                }
            } catch (Exception e) {
                // 单片推进失败不影响其他排队 run
                log.error("SERIAL排队run推进异常，RunID: {}", queued.getId(), e);
            }
        }
    }

    /**
     * 判断某排队 run 是否满足推进条件：其所属任务已无其它 running/pending 兄弟
     *
     * @param queued 排队中的 run
     * @return true=可推进
     */
    boolean canPromote(TaskRun queued) {
        long activeSiblings = taskRunMapper.selectCount(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTaskId, queued.getTaskId())
                .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending));
        return activeSiblings == 0;
    }

    /**
     * 把排队 run 有条件地推进为 pending（设置开始时间）
     * 以 queued 为状态条件更新，保证同一 run 只被推进一次，天然规避并发推进
     *
     * @param runId 排队 run ID
     * @return true=推进成功
     */
    boolean promoteToPending(String runId) {
        int updated = taskRunMapper.update(null, new LambdaUpdateWrapper<TaskRun>()
                .eq(TaskRun::getId, runId)
                .eq(TaskRun::getStatus, TaskRunStatus.queued)
                .set(TaskRun::getStatus, TaskRunStatus.pending)
                .set(TaskRun::getStartTime, LocalDateTime.now()));
        return updated > 0;
    }
}
