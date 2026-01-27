package com.sjdt.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskStopMessage;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.ITaskService;
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

    @Autowired
    public TaskTimeoutMonitor(TaskRunMapper taskRunMapper, TaskMapper taskMapper, 
                              ITaskService taskService, TaskProducerService taskProducerService) {
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.taskService = taskService;
        this.taskProducerService = taskProducerService;
    }

    /**
     * 每30秒执行一次超时检查
     */
    @Scheduled(fixedRate = 30000)
    public void checkTimeoutTasks() {
        log.debug("开始执行任务超时检查...");
        
        // 查询所有处于 running 状态的任务实例
        // 这里只查询 running 状态，因为 pending 状态可能是在排队，暂时不计入执行超时
        List<TaskRun> runningTasks = taskRunMapper.selectList(
                new LambdaQueryWrapper<TaskRun>()
                        .eq(TaskRun::getStatus, TaskRunStatus.running)
        );

        if (runningTasks.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        for (TaskRun taskRun : runningTasks) {
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
            // 任务定义已被删除，标记运行实例为 failed
            log.warn("任务定义不存在，标记实例为失败，TaskRunID: {}", taskRun.getId());
            updateTaskRunStatus(taskRun, TaskRunStatus.failed, "任务定义已被删除");
            return;
        }

        Integer timeoutSeconds = task.getTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            // 未配置超时或超时为0（不限制），跳过
            return;
        }

        // 计算运行时长
        LocalDateTime startTime = taskRun.getStartTime();
        if (startTime == null) {
            // 还没有开始时间（可能是刚从 pending 转 running 还没更新时间？或者数据异常）
            // 使用 createTime 兜底
            startTime = taskRun.getCreateTime();
        }

        long runSeconds = Duration.between(startTime, now).getSeconds();
        if (runSeconds > timeoutSeconds) {
            log.warn("任务执行超时，TaskRunID: {}, TaskID: {}, 已运行: {}s, 限制: {}s", 
                    taskRun.getId(), task.getId(), runSeconds, timeoutSeconds);

            // 1. 发送停止消息给 Worker (尽力而为)
            sendStopMessage(taskRun, "任务执行超时，系统强制停止");

            // 2. 更新数据库状态为 timeout
            updateTaskRunStatus(taskRun, TaskRunStatus.timeout, "任务执行超时 (限制: " + timeoutSeconds + "s)");

            // 3. 触发重试逻辑
            triggerRetry(task, taskRun);
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

    private void triggerRetry(Task task, TaskRun failedRun) {
        Integer maxRetryCount = task.getRetryCount();
        if (maxRetryCount == null || maxRetryCount <= 0) {
            return;
        }

        Integer currentRetryNum = failedRun.getRetryNum();
        if (currentRetryNum == null) currentRetryNum = 0;

        if (currentRetryNum < maxRetryCount) {
            int nextRetryNum = currentRetryNum + 1;
            int intervalSeconds = task.getRetryIntervalSeconds() != null ? task.getRetryIntervalSeconds() : 60;

            log.info("超时任务准备重试。TaskID: {}, 下次重试次数: {}, 间隔: {}秒", 
                    task.getId(), nextRetryNum, intervalSeconds);

            // 改用 RocketMQ 延时消息
            taskService.publishTaskWithRetryAndDelay(task.getId(), nextRetryNum, intervalSeconds);
        } else {
            log.info("超时任务已达到最大重试次数，不再重试。TaskID: {}", task.getId());
        }
    }
}
