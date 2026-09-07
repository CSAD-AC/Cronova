package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 任务执行实例终态赋值器（状态机单向迁移）
 * <p>
 * 收敛"把 run 推进到终态"的写入路径：只允许从 {@code pending / running} 单向迁移到终态，
 * 解决两类历史隐患：
 * <ol>
 *     <li><b>迟到结果覆盖新状态</b>：超时监控已把 run 置 timeout 并重发、或故障转移已判 failed 后，
 *         旧 Worker 迟到的 success/failed 结果到达时，条件更新更新 0 行被直接丢弃，状态不回退；</li>
 *     <li><b>重复消息双触发重试</b>：唯一成功写入的调用方才会继续触发结算/重试，重复消息不再叠加计数。</li>
 * </ol>
 * 使用方式：调用方先构造携带终态字段（status/exitCode/endTime/durationMs/errorMessage 等）的 run，
 * 传入 {@link #tryAdvanceToTerminal(TaskRun)}；返回 true 表示本次写入生效、可继续后续（结算/重试），
 * 返回 false 表示 run 已被其他路径推进为终态（迟到/重复结果），应直接丢弃。
 */
@Slf4j
@Service
public class TaskRunFinalizer {

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;

    @Autowired
    public TaskRunFinalizer(TaskRunMapper taskRunMapper) {
        this.taskRunMapper = taskRunMapper;
    }

    /**
     * 把 run 单向迁移到终态：仅当目标 run 当前状态是 {@code pending} 或 {@code running} 时更新成功。
     * <p>
     * 采用条件更新而非全字段 updateById，从 SQL 层面保证"终态只能由非终态推进而来"，
     * 防止已进入终态（被超时/转移/其他结果推进）的 run 被旧结果篡改。
     *
     * @param run 携带主键与终态字段的任务运行实例（startTime 不在写入范围内，保留原值）
     * @return true=更新生效；false=run 已处于终态，本次为迟到/重复结果，应丢弃
     */
    public boolean tryAdvanceToTerminal(TaskRun run) {
        if (run == null || run.getId() == null || run.getStatus() == null) {
            return false;
        }
        int updated = taskRunMapper.update(null, new LambdaUpdateWrapper<TaskRun>()
                .eq(TaskRun::getId, run.getId())
                .in(TaskRun::getStatus, TaskRunStatus.pending, TaskRunStatus.running)
                .set(TaskRun::getStatus, run.getStatus())
                .set(TaskRun::getWorkerId, run.getWorkerId())
                .set(TaskRun::getExitCode, run.getExitCode())
                .set(TaskRun::getEndTime, run.getEndTime())
                .set(TaskRun::getDurationMs, run.getDurationMs())
                .set(TaskRun::getErrorMessage, run.getErrorMessage())
                .set(TaskRun::getRetryNum, run.getRetryNum())
                .set(TaskRun::getLastHeartbeatTime, run.getLastHeartbeatTime())
        );
        return updated > 0;
    }

    /**
     * 判断状态是否为终态（success / failed / timeout）
     * <p>
     * queued/skipped 是中间态，不视为终态。
     *
     * @param status 运行状态
     * @return true=终态
     */
    public boolean isTerminal(TaskRunStatus status) {
        return status == TaskRunStatus.success
                || status == TaskRunStatus.failed
                || status == TaskRunStatus.timeout;
    }
}