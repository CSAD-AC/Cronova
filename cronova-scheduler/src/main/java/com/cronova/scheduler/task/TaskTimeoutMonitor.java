package com.cronova.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.common.RedisKey;
import com.cronova.scheduler.common.SchedulerLocked;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.TaskStopMessage;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.service.ITaskService;
import com.cronova.scheduler.service.ShardSettlementService;
import com.cronova.scheduler.service.TaskRunFinalizer;
import com.cronova.scheduler.service.TaskProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务超时监控器
 * 定时扫描运行中的任务，检查是否超过设定的超时时间
 * 如果超时，则标记为失败并尝试重试
 * <p>
 * 超时阈值见 application.yml 的 cronova.timeout.*：
 * <ol>
 *   <li>派发超时 {@code dispatch-seconds}：run 保持 pending 超过该值判定派发失败；</li>
 *   <li>执行超时：总时长超过 {@code execution-max-seconds}（任务级 timeoutSeconds 可覆盖），
 *       或超过 {@code execution-heartbeat-seconds} 无状态上报。</li>
 * </ol>
 * 超时后发送停止消息、标记 timeout 并触发重试。
 */
@Slf4j
@Component
public class TaskTimeoutMonitor {

    private final TaskRunMapper taskRunMapper;
    private final TaskMapper taskMapper;
    private final ITaskService taskService;
    private final TaskProducerService taskProducerService;
    private final ShardSettlementService shardSettlementService;
    private final TaskRunFinalizer taskRunFinalizer;

    /** 派发超时（秒） */
    @Value("${cronova.timeout.dispatch-seconds:60}")
    private int dispatchTimeoutSeconds;

    /** 执行总时长上限（秒） */
    @Value("${cronova.timeout.execution-max-seconds:3600}")
    private int executionMaxSeconds;

    /** 无心跳阈值（秒） */
    @Value("${cronova.timeout.execution-heartbeat-seconds:30}")
    private int executionHeartbeatSeconds;

    @Autowired
    public TaskTimeoutMonitor(TaskRunMapper taskRunMapper, TaskMapper taskMapper,
                              ITaskService taskService, TaskProducerService taskProducerService,
                              TaskRunFinalizer taskRunFinalizer,
                              ShardSettlementService shardSettlementService) {
        this.taskRunFinalizer = taskRunFinalizer;
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.taskProducerService = taskProducerService;
        this.shardSettlementService = shardSettlementService;
    }

    /**
     * 每30秒执行一次超时检查
     * <p>
     * @SchedulerLocked 分布式锁串行化多实例：同一时刻只有一个实例做超时判定，
     * 避免重复发 STOP / 重复标记超时（锁 TTL 按周期自动推导为 24s）。
     */
    @Scheduled(fixedRate = 30000)
    @SchedulerLocked(RedisKey.SCHEDULER_LOCK_TIMEOUT)
    public void checkTimeoutTasks() {
        log.debug("开始执行任务超时检查...");

        // 查询所有处于 running 或 pending 状态的任务实例
        List<TaskRun> activeTaskRuns = taskRunMapper.selectList(
                new LambdaQueryWrapper<TaskRun>()
                        .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending)
        );

        if (activeTaskRuns.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (TaskRun taskRun : activeTaskRuns) {
            try {
                checkSingleTask(taskRun, now);
            } catch (Exception e) {
                log.error("检查任务超时异常，TaskRunID: {}", taskRun.getId(), e);
            }
        }
    }

    private void checkSingleTask(TaskRun taskRun, LocalDateTime now) {
        // 获取任务配置
        Task task = taskMapper.selectById(taskRun.getTaskId());
        if (task == null) {
            log.warn("任务定义不存在，标记实例为失败，TaskRunID: {}", taskRun.getId());
            if (!updateTaskRunStatus(taskRun, TaskRunStatus.failed, "任务定义已被删除")) {
                return;
            }
            // 终态 -> 触发分片结算检查（任务已删除无重试预算，结算服务会按最终失败处理）
            shardSettlementService.onRunTerminal(taskRun.getId());
            return;
        }

        if (taskRun.getStatus() == TaskRunStatus.pending) {
            // 派发超时：pending 停留超过阈值，说明执行器未领用
            long pendingSeconds = Duration.between(taskRun.getCreateTime(), now).getSeconds();
            if (pendingSeconds > dispatchTimeoutSeconds) {
                log.warn("任务派发超时（执行器未领用），TaskRunID: {}, TaskID: {}, 已等待: {}s, 限制: {}s",
                        taskRun.getId(), task.getId(), pendingSeconds, dispatchTimeoutSeconds);

                handleTimeoutRetry(task, taskRun, "任务派发超时（执行器未响应）");
            }
            return;
        }

        // 执行超时：总时长上限，任务级 timeoutSeconds 优先，否则用全局默认
        long maxDurationSeconds = (task.getTimeoutSeconds() != null && task.getTimeoutSeconds() > 0)
                ? task.getTimeoutSeconds() : executionMaxSeconds;

        LocalDateTime start = taskRun.getStartTime() != null ? taskRun.getStartTime() : taskRun.getCreateTime();

        // 总时长超时（从 run 开始时间起累计）
        long durationSeconds = Duration.between(start, now).getSeconds();
        if (durationSeconds > maxDurationSeconds) {
            log.warn("任务执行超时(总时长)，TaskRunID: {}, TaskID: {}, 已运行: {}s, 限制: {}s",
                    taskRun.getId(), task.getId(), durationSeconds, maxDurationSeconds);

            sendStopMessage(taskRun, "任务执行超时(总时长限制)，系统强制停止");
            handleTimeoutRetry(task, taskRun, "任务执行超时(总时长: " + maxDurationSeconds + "s)");
            return;
        }

        // 无心跳超时：lastHeartbeatTime 由 Worker 的状态上报刷新
        LocalDateTime heartbeat = taskRun.getLastHeartbeatTime() != null
                ? taskRun.getLastHeartbeatTime() : start;
        long inactiveSeconds = Duration.between(heartbeat, now).getSeconds();
        if (inactiveSeconds > executionHeartbeatSeconds) {
            log.warn("任务执行超时(无心跳)，TaskRunID: {}, TaskID: {}, 已: {}s 无状态上报, 限制: {}s",
                    taskRun.getId(), task.getId(), inactiveSeconds, executionHeartbeatSeconds);

            sendStopMessage(taskRun, "任务执行超时(无心跳)，系统强制停止");
            handleTimeoutRetry(task, taskRun, "任务执行超时(无心跳: " + executionHeartbeatSeconds + "s)");
        }
    }

    private boolean updateTaskRunStatus(TaskRun taskRun, TaskRunStatus status, String errorMsg) {
        LocalDateTime endTime = LocalDateTime.now();
        Long durationMs = taskRun.getStartTime() == null
                ? null : Duration.between(taskRun.getStartTime(), endTime).toMillis();
        TaskRun terminal = TaskRun.builder()
                .id(taskRun.getId())
                .workerId(taskRun.getWorkerId())
                .status(status)
                .endTime(endTime)
                .durationMs(durationMs)
                .errorMessage(errorMsg)
                .retryNum(taskRun.getRetryNum())
                .lastHeartbeatTime(LocalDateTime.now())
                .build();
        if (!taskRunFinalizer.tryAdvanceToTerminal(terminal)) {
            return false;
        }
        taskRun.setStatus(status);
        taskRun.setErrorMessage(errorMsg);
        taskRun.setEndTime(endTime);
        taskRun.setDurationMs(durationMs);
        return true;
    }

    private void sendStopMessage(TaskRun taskRun, String reason) {
        if (taskRun.getWorkerId() == null) return;

        try {
            TaskStopMessage stopMsg = TaskStopMessage.builder()
                    .taskId(taskRun.getTaskId())
                    .taskRunId(taskRun.getId())
                    .targetWorkerId(taskRun.getWorkerId())
                    .reason(reason)
                    .build();
            taskProducerService.sendTaskControl(stopMsg);
            log.info("已发送停止消息给Worker: {}, TaskRunID: {}", taskRun.getWorkerId(), taskRun.getId());
        } catch (Exception e) {
            log.error("发送停止消息失败", e);
        }
    }

    /**
     * 处理超时重试逻辑
     * 超时实例保持终态，重试通过新 run 发布
     */
    private void handleTimeoutRetry(Task task, TaskRun taskRun, String reason) {
        Integer oldRetryNum = taskRun.getRetryNum();
        if (oldRetryNum == null) oldRetryNum = 0;

        // 1. 更新状态为超时 (这一步是为了如果后续逻辑都成功，数据库状态是正确的)
        if (!updateTaskRunStatus(taskRun, TaskRunStatus.timeout, reason)) {
            log.info("超时状态写入失败，run已被其他路径推进终态，跳过重试，TaskRunID: {}", taskRun.getId());
            return;
        }

        // 终态 -> 触发分片结算检查：若该 run 的 retryNum 仍有重试预算（即将创建新 run），
        // 结算服务内部会跳过，不会提前判死批次；预算耗尽时才真正结算
        shardSettlementService.onRunTerminal(taskRun.getId());

        Integer maxRetryCount = task.getRetryCount();
        if (maxRetryCount == null || maxRetryCount <= 0) {
            return;
        }

        if (oldRetryNum < maxRetryCount) {
            int nextRetryNum = oldRetryNum + 1;
            int intervalSeconds = task.getRetryIntervalSeconds() != null ? task.getRetryIntervalSeconds() : 60;

            log.info("超时任务准备创建新运行实例。TaskID: {}, 原TaskRunID: {}, 下次重试次数: {}, 间隔: {}秒",
                    task.getId(), taskRun.getId(), nextRetryNum, intervalSeconds);

            try {
                taskService.publishTaskWithRetryAndDelay(
                        task.getId(), nextRetryNum, intervalSeconds,
                        taskRun.getShardIndex(), taskRun.getShardTotal(), taskRun.getShardBatchId());
            } catch (Exception e) {
                log.error("超时重试创建新运行实例失败，TaskID: {}, 原TaskRunID: {}",
                        task.getId(), taskRun.getId(), e);
            }
        } else {
            log.info("超时任务已达到最大重试次数，不再重试。TaskID: {}, TaskRunID: {}", task.getId(), taskRun.getId());
        }
    }
}
