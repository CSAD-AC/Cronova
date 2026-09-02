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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务超时监控器
 * 定时扫描运行中的任务，检查是否超过设定的超时时间
 * 如果超时，则标记为失败并尝试重试
 */
@Slf4j
@Component
public class TaskTimeoutMonitor {

    private final TaskRunMapper taskRunMapper;
    private final TaskMapper taskMapper;
    private final ITaskService taskService;
    private final TaskProducerService taskProducerService;
    private final ShardSettlementService shardSettlementService;

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
            // 检查派发超时：如果任务在 pending 状态停留太久（例如超过 120 秒执行器仍未领用）
            long pendingSeconds = Duration.between(taskRun.getCreateTime(), now).getSeconds();
            if (pendingSeconds > 120) { // 默认 120 秒派发超时
                log.warn("任务派发超时（执行器未领用），TaskRunID: {}, TaskID: {}, 已等待: {}s", 
                        taskRun.getId(), task.getId(), pendingSeconds);
                
                handleTimeoutRetry(task, taskRun, "任务派发超时（执行器未响应）");
            }
            return;
        }

        // 以下是针对 running 状态的执行超时检查
        Integer timeoutSeconds = task.getTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return;
        }

        // 计算运行时长，优先使用 lastHeartbeatTime
        LocalDateTime lastActiveTime = taskRun.getLastHeartbeatTime();
        if (lastActiveTime == null) {
            lastActiveTime = taskRun.getStartTime();
        }
        if (lastActiveTime == null) {
            lastActiveTime = taskRun.getCreateTime();
        }

        long inactiveSeconds = Duration.between(lastActiveTime, now).getSeconds();
        if (inactiveSeconds > timeoutSeconds) {
            log.warn("任务执行超时(无心跳)，TaskRunID: {}, TaskID: {}, 已由于: {}s 无响应, 限制: {}s", 
                    taskRun.getId(), task.getId(), inactiveSeconds, timeoutSeconds);

            // 1. 发送停止消息给 Worker (尽力而为)
            sendStopMessage(taskRun, "任务执行超时，系统强制停止");

            // 2. 处理超时重试
            handleTimeoutRetry(task, taskRun, "任务执行超时 (限制: " + timeoutSeconds + "s)");
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
