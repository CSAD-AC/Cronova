package com.sjdt.scheduler.task;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.*;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.TaskProducerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时任务调度器
 * 负责扫描数据库中启用的Cron任务，根据Cron表达式判断是否需要执行
 * 支持两种执行类型：
 * 1. script：发送任务消息到RocketMQ，由Worker执行
 * 2. callback：直接执行HTTP回调请求
 */
@Slf4j
@Component
public class CronTaskScheduler {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskRunMapper taskRunMapper;

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    @Autowired
    private TaskProducerService taskProducerService;

    @Autowired
    private TaskCallbackExecutor taskCallbackExecutor;

    @Autowired
    private ScriptMapper scriptMapper;

    private final CronParser cronParser;

    private final Map<String, ExecutionTime> executionTimeCache = new ConcurrentHashMap<>();

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    public CronTaskScheduler() {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        this.cronParser = new CronParser(cronDefinition);
    }

    /**
     * 扫描并执行到期的定时任务
     * 每60秒调用一次
     */
    public void scanAndExecuteTasks() {
        log.info("开始扫描定时任务");
        LocalDateTime now = LocalDateTime.now();

        try {
            List<Task> cronTasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                    .eq(Task::getTaskType, TaskType.cron)
                    .eq(Task::getEnabled, true)
                    .eq(Task::getStatus, com.sjdt.scheduler.domain.enums.TaskStatus.active)
            );

            log.info("找到{}个启用的Cron任务", cronTasks.size());

            int executeCount = 0;
            for (Task task : cronTasks) {
                try {
                    if (shouldExecuteNow(task, now)) {
                        executeTask(task);
                        executeCount++;
                    }
                } catch (Exception e) {
                    log.error("执行定时任务失败，任务ID: {}, 任务名称: {}", task.getId(), task.getName(), e);
                }
            }

            log.info("定时任务扫描完成，本次执行{}个任务", executeCount);

        } catch (Exception e) {
            log.error("扫描定时任务时发生异常", e);
        }
    }

    /**
     * 根据Cron表达式判断任务是否应该执行
     *
     * @param task 任务对象
     * @param now 当前时间
     * @return true表示需要执行
     */
    private boolean shouldExecuteNow(Task task, LocalDateTime now) {
        String cronExpr = task.getCronExpr();
        if (cronExpr == null || cronExpr.isEmpty()) {
            log.warn("任务{}的Cron表达式为空", task.getId());
            return false;
        }

        try {
            ExecutionTime executionTime = getExecutionTime(cronExpr);
            ZonedDateTime zonedNow = now.atZone(ZONE_ID);

            Optional<ZonedDateTime> lastExecutionOpt = executionTime.lastExecution(zonedNow);

            if (lastExecutionOpt.isEmpty()) {
                return true;
            }

            ZonedDateTime lastExecution = lastExecutionOpt.get();
            long lastMillis = lastExecution.toInstant().toEpochMilli();
            long nowMillis = zonedNow.toInstant().toEpochMilli();

            boolean shouldExecute = (nowMillis - lastMillis < 60000) && (nowMillis >= lastMillis);

            if (shouldExecute) {
                log.debug("任务{}应该执行，LastExecution: {}", task.getId(), lastExecution);
            }

            return shouldExecute;

        } catch (Exception e) {
            log.error("解析Cron表达式失败，任务ID: {}, Cron: {}", task.getId(), cronExpr, e);
            return false;
        }
    }

    /**
     * 获取ExecutionTime实例（带缓存）
     *
     * @param cronExpr Cron表达式
     * @return ExecutionTime实例
     */
    private ExecutionTime getExecutionTime(String cronExpr) {
        return executionTimeCache.computeIfAbsent(cronExpr, expr -> {
            try {
                Cron cron = cronParser.parse(expr);
                return ExecutionTime.forCron(cron);
            } catch (Exception e) {
                log.error("解析Cron表达式失败: {}", expr, e);
                throw new RuntimeException("解析Cron表达式失败: " + expr, e);
            }
        });
    }

    /**
     * 执行定时任务
     * 根据executeType分发到不同的执行器
     *
     * @param task 任务对象
     */
    private void executeTask(Task task) {
        log.info("开始执行定时任务，任务ID: {}, 任务名称: {}, 执行类型: {}",
            task.getId(), task.getName(), task.getExecuteType());

        String taskRunId = createTaskRun(task);

        if (task.getExecuteType() == ExecuteType.callback) {
            executeCallbackTask(task, taskRunId);
        } else {
            executeScriptTask(task, taskRunId);
        }
    }

    /**
     * 创建任务运行记录
     *
     * @param task 任务对象
     * @return 任务运行实例ID
     */
    private String createTaskRun(Task task) {
        TaskRun taskRun = TaskRun.builder()
            .taskId(task.getId())
            .status(TaskRunStatus.pending)
            .createTime(LocalDateTime.now())
            .build();
        taskRunMapper.insert(taskRun);
        log.info("创建任务运行记录成功，运行ID: {}", taskRun.getId());
        return taskRun.getId();
    }

    /**
     * 执行脚本任务
     * 发送任务消息到RocketMQ
     *
     * @param task 任务对象
     * @param taskRunId 任务运行实例ID
     */
    private void executeScriptTask(Task task, String taskRunId) {
        try {
            Script script = null;
            if (task.getScriptId() != null && !task.getScriptId().isEmpty()) {
                script = scriptMapper.selectById(task.getScriptId());
            }

            TaskExecuteMessage.ScriptInfo scriptInfo = TaskExecuteMessage.ScriptInfo.builder()
                .id(script != null ? script.getId() : null)
                .name(script != null ? script.getName() : null)
                .path(script != null ? script.getPath() : null)
                .build();

            TaskExecuteMessage message = TaskExecuteMessage.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskRunId(taskRunId)
                .description(task.getDescription())
                .scriptType(task.getScriptType() != null ? task.getScriptType().name() : null)
                .script(scriptInfo)
                .taskType(TaskType.cron.name())
                .cronExpr(task.getCronExpr())
                .targetWorkerId(task.getTargetWorker())
                .timeoutSeconds(task.getTimeoutSeconds())
                .build();

            taskProducerService.sendTask(message);
            log.info("脚本任务消息已发送，任务ID: {}, 运行ID: {}", task.getId(), taskRunId);

        } catch (Exception e) {
            log.error("发送脚本任务消息失败，任务ID: {}", task.getId(), e);
            updateTaskRunStatus(taskRunId, TaskRunStatus.failed, e.getMessage());
        }
    }

    /**
     * 执行HTTP回调任务
     * 直接调用回调执行器
     *
     * @param task 任务对象
     * @param taskRunId 任务运行实例ID
     */
    private void executeCallbackTask(Task task, String taskRunId) {
        try {
            CallbackJobConfig config = getCallbackConfigByTaskId(task.getId());
            if (config == null) {
                log.warn("回调配置不存在，任务ID: {}", task.getId());
                updateTaskRunStatus(taskRunId, TaskRunStatus.failed, "回调配置不存在");
                return;
            }

            Map<String, Object> requestData = new HashMap<>();
            requestData.put("taskName", task.getName());
            requestData.put("taskDescription", task.getDescription());

            CallbackJobExecuteMessage message = CallbackJobExecuteMessage.builder()
                .taskId(task.getId())
                .taskRunId(taskRunId)
                .configId(config.getId())
                .callbackUrl(config.getCallbackUrl())
                .callbackMethod(config.getCallbackMethod())
                .secretKey(config.getSecretKey())
                .timeoutSeconds(config.getTimeoutSeconds())
                .retryCount(config.getRetryCount())
                .retryIntervalSeconds(config.getRetryIntervalSeconds())
                .taskResult(null)
                .requestData(requestData)
                .triggerTime(LocalDateTime.now())
                .build();

            taskCallbackExecutor.executeCallbackJob(message);
            log.info("HTTP回调任务已执行，任务ID: {}, 运行ID: {}, URL: {}",
                task.getId(), taskRunId, config.getCallbackUrl());

        } catch (Exception e) {
            log.error("执行HTTP回调任务失败，任务ID: {}", task.getId(), e);
            updateTaskRunStatus(taskRunId, TaskRunStatus.failed, e.getMessage());
        }
    }

    /**
     * 根据任务ID获取回调配置
     *
     * @param taskId 任务ID
     * @return 回调配置
     */
    private CallbackJobConfig getCallbackConfigByTaskId(String taskId) {
        List<CallbackJobConfig> configs = callbackJobConfigMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                .eq(CallbackJobConfig::getTaskId, taskId)
        );
        return configs.isEmpty() ? null : configs.get(0);
    }

    /**
     * 更新任务运行状态
     *
     * @param taskRunId 任务运行实例ID
     * @param status 状态
     * @param errorMessage 错误信息
     */
    private void updateTaskRunStatus(String taskRunId, TaskRunStatus status, String errorMessage) {
        try {
            TaskRun taskRun = taskRunMapper.selectById(taskRunId);
            if (taskRun != null) {
                taskRun.setStatus(status);
                taskRun.setEndTime(LocalDateTime.now());
                taskRun.setErrorMessage(errorMessage);
                taskRunMapper.updateById(taskRun);
            }
        } catch (Exception e) {
            log.error("更新任务运行状态失败，运行ID: {}", taskRunId, e);
        }
    }

    /**
     * 清理过期的ExecutionTime缓存
     * 建议定时调用，如每天凌晨
     */
    public void clearCache() {
        executionTimeCache.clear();
        log.info("已清理Cron表达式缓存");
    }
}
