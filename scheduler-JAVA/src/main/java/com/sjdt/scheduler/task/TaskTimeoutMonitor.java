package com.sjdt.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskStopMessage;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.ShardSettlementService;
import com.sjdt.scheduler.service.TaskProducerService;
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
 * 超时阈值见 application.yml 的 sjdt.timeout.*：
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

    /** 派发超时（秒） */
    @Value("${sjdt.timeout.dispatch-seconds:60}")
    private int dispatchTimeoutSeconds;

    /** 执行总时长上限（秒） */
    @Value("${sjdt.timeout.execution-max-seconds:3600}")
    private int executionMaxSeconds;

    /** 无心跳阈值（秒） */
    @Value("${sjdt.timeout.execution-heartbeat-seconds:30}")
    private int executionHeartbeatSeconds;

    @Autowired
    public TaskTimeoutMonitor(TaskRunMapper taskRunMapper, TaskMapper taskMapper,
                              ITaskService taskService, TaskProducerService taskProducerService,
                              ShardSettlementService shardSettlementService) {
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.taskProducerService = taskProducerService;
        this.shardSettlementService = shardSettlementService;
    }

    /**
     * 每30秒执行一次超时检查
     */
    @Scheduled(fixedRate = 30000)
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
            updateTaskRunStatus(taskRun, TaskRunStatus.failed, "任务定义已被删除");
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

    private void updateTaskRunStatus(TaskRun taskRun, TaskRunStatus status, String errorMsg) {
        taskRun.setStatus(status);
        taskRun.setErrorMessage(errorMsg);
        taskRun.setEndTime(LocalDateTime.now());
        if (taskRun.getStartTime() != null) {
            taskRun.setDurationMs(Duration.between(taskRun.getStartTime(), taskRun.getEndTime()).toMillis());
        }
        taskRunMapper.updateById(taskRun);
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
     * 超时情况下，不创建新实例，而是重用现有实例ID重新发布
     */
    private void handleTimeoutRetry(Task task, TaskRun taskRun, String reason) {
        // 保存旧状态以便回滚
        TaskRunStatus oldStatus = taskRun.getStatus();
        Integer oldRetryNum = taskRun.getRetryNum();
        if (oldRetryNum == null) oldRetryNum = 0;

        // 1. 更新状态为超时 (这一步是为了如果后续逻辑都成功，数据库状态是正确的)
        updateTaskRunStatus(taskRun, TaskRunStatus.timeout, reason);

        // 终态 -> 触发分片结算检查：若该 run 的 retryNum 仍有重试预算（即将 republish），
        // 结算服务内部会跳过，不会提前判死批次；预算耗尽时才真正结算
        shardSettlementService.onRunTerminal(taskRun.getId());

        Integer maxRetryCount = task.getRetryCount();
        if (maxRetryCount == null || maxRetryCount <= 0) {
            return;
        }

        if (oldRetryNum < maxRetryCount) {
            int nextRetryNum = oldRetryNum + 1;
            int intervalSeconds = task.getRetryIntervalSeconds() != null ? task.getRetryIntervalSeconds() : 60;

            log.info("超时任务准备重试(重用实例)。TaskID: {}, TaskRunID: {}, 下次重试次数: {}, 间隔: {}秒", 
                    task.getId(), taskRun.getId(), nextRetryNum, intervalSeconds);

            // 2. 更新重试次数到数据库 (因为Worker/ResultConsumer会从数据库读取此值)
            taskRun.setRetryNum(nextRetryNum);
            taskRunMapper.updateById(taskRun);

            // 3. 重新发布现有实例 (尝试发送MQ)
            try {
                taskService.republishTaskRun(taskRun.getId(), intervalSeconds);
            } catch (Exception e) {
                log.error("超时重试发送MQ失败，回滚任务状态以等待下一次扫描。TaskRunID: {}", taskRun.getId(), e);
                
                // 4. 发送失败，回滚状态
                taskRun.setStatus(oldStatus);
                taskRun.setRetryNum(oldRetryNum); // 回滚重试次数
                taskRun.setErrorMessage(null); // 清除错误信息，或者记录MQ失败
                taskRun.setEndTime(null); // 清除结束时间
                taskRun.setDurationMs(null); // 清除耗时
                taskRunMapper.updateById(taskRun);
            }
        } else {
            log.info("超时任务已达到最大重试次数，不再重试。TaskID: {}, TaskRunID: {}", task.getId(), taskRun.getId());
        }
    }
}
