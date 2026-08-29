package com.sjdt.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.*;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.TaskDispatchService;
import com.sjdt.scheduler.util.CronNextTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务调度器
 * <p>
 * 核心调度逻辑：
 * <ul>
 *     <li><b>到期驱动扫描</b>：按 status + task_type + next_trigger_time 索引查询"未来窗口内到期"的任务，
 *         替代旧的"全量扫 active + Redis 缓存 + 60s 窗口判断"方案；</li>
 *     <li><b>CAS 抢占触发</b>：以 next_trigger_time 旧值作乐观锁条件更新，
 *         即使分布式锁失效、双实例同时扫同一行，也只允许一次 UPDATE 成功；</li>
 *     <li><b>misfire 补偿</b>：理论触发点落后超过阈值时放弃补跑、对齐未来，防止雪崩式补跑。</li>
 * </ul>
 * 触发成功后走现有 executeTask 派发。
 */
@Slf4j
@Component
public class CronTaskScheduler {

    /** 到期扫描窗口（秒）：本次扫描把未来 5s 内到期的任务纳入，与扫描周期对齐 */
    private static final int SCAN_WINDOW_SECONDS = 5;

    /** misfire 阈值（分钟）：理论触发点落后超过该值视为 misfire，放弃补跑、对齐未来 */
    private static final int MISFIRE_THRESHOLD_MINUTES = 5;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private TaskRunMapper taskRunMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    @Autowired
    private TaskDispatchService taskDispatchService;

    @Autowired
    private TaskCallbackExecutor taskCallbackExecutor;

    @Autowired
    private ScriptMapper scriptMapper;

    @Autowired
    private IScriptService scriptService;

    /**
     * 到期驱动扫描：查询窗口内到期的活跃 cron 任务并逐个触发
     * 由 ScheduledTaskRunnerTask 每 5s 调用（持分布式锁的实例才会进入这里）
     */
    public void scanAndTrigger() {
        LocalDateTime now = LocalDateTime.now();
        List<Task> dueTasks = taskService.list(new LambdaQueryWrapper<Task>()
                .eq(Task::getStatus, TaskStatus.active)
                .eq(Task::getTaskType, TaskType.cron)
                .le(Task::getNextTriggerTime, now.plusSeconds(SCAN_WINDOW_SECONDS)));

        if (dueTasks.isEmpty()) {
            return;
        }
        log.info("到期扫描：{}个任务进入触发窗口", dueTasks.size());

        for (Task task : dueTasks) {
            try {
                trigger(task, now);
            } catch (Exception e) {
                log.error("触发定时任务失败，任务ID: {}, 任务名称: {}", task.getId(), task.getName(), e);
            }
        }
    }

    /**
     * 触发单个任务：misfire 判定 + CAS 抢占
     * <p>
     * CAS（乐观锁）：UPDATE tasks SET next_trigger_time=?, last_trigger_time=?
     *   WHERE id=? AND next_trigger_time=? AND status='active'
     * 影响行数 = 0 说明 next_trigger_time 已被其他实例推进（重复触发被挡下），直接放弃。
     * 这就是分布式锁之外的"正确性最后防线"。
     */
    private void trigger(Task task, LocalDateTime now) {
        LocalDateTime firedAt = task.getNextTriggerTime();
        if (firedAt == null) {
            // 防御：理论触发点为空（回填已兜底，理论上不会发生），跳过避免 NPE
            return;
        }

        LocalDateTime next;
        // misfire 补偿：理论触发点已落后超过阈值 -> 放弃补跑本拍，直接对齐未来
        if (firedAt.isBefore(now.minusMinutes(MISFIRE_THRESHOLD_MINUTES))) {
            next = CronNextTimeUtil.next(now, task.getCronExpr());
            log.warn("misfire: 任务{}落后超过{}分钟，跳过补跑对齐至{}", task.getId(), MISFIRE_THRESHOLD_MINUTES, next);
        } else {
            next = CronNextTimeUtil.next(firedAt, task.getCronExpr());
        }

        // CAS 抢占：只有 next_trigger_time 仍等于本次扫描读到的旧值（未被其他实例推进）才能更新成功
        int won = taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getId, task.getId())
                .eq(Task::getNextTriggerTime, firedAt)   // 乐观锁条件：旧值
                .eq(Task::getStatus, TaskStatus.active)
                .set(Task::getNextTriggerTime, next)
                .set(Task::getLastTriggerTime, now));
        if (won == 0) {
            log.debug("CAS抢占失败，任务{}已被其他实例触发，跳过", task.getId());
            return;
        }

        log.info("CAS抢占成功，触发任务，任务ID: {}, 名称: {}, 执行类型: {}",
                task.getId(), task.getName(), task.getExecuteType());
        // 抢占成功 -> 派发执行
        executeTask(task);
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
                .startTime(LocalDateTime.now())
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
                .isDirectory(script != null ? script.getIsDirectory() : null)
                .mainFile(script != null ? script.getMainFile() : null)
                .parentId(script != null ? script.getParentId() : null)
                .rootPath(getRootPath(script))
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

            // 统一派发入口：路由选 Worker + Tag 定向投递；无可用 Worker 则 park（run 保持 pending）
            boolean dispatched = taskDispatchService.dispatch(task, message, 0);
            if (dispatched) {
                log.info("脚本任务消息已定向发送，任务ID: {}, 运行ID: {}", task.getId(), taskRunId);
            } else {
                log.warn("脚本任务无可用Worker，已park，任务ID: {}, 运行ID: {}（交派发超时监控兜底）",
                        task.getId(), taskRunId);
            }

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
                .timeoutSeconds(task.getTimeoutSeconds())
                .retryCount(task.getRetryCount())
                .retryIntervalSeconds(task.getRetryIntervalSeconds())
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
            new LambdaQueryWrapper<CallbackJobConfig>()
                .eq(CallbackJobConfig::getTaskId, taskId)
        );
        return configs.isEmpty() ? null : configs.get(0);
    }

    /**
     * 更新任务运行状态
     * 修正：增加重试触发逻辑，确保同步执行失败的任务也能重试
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
                log.info("任务运行状态已更新，RunID: {}, Status: {}", taskRunId, status);

                // 如果是失败状态，触发重试逻辑
                if (status == TaskRunStatus.failed || status == TaskRunStatus.timeout) {
                    Task task = taskService.getById(taskRun.getTaskId());
                    if (task != null) {
                        triggerRetry(task, taskRun);
                    }
                }
            }
        } catch (Exception e) {
            log.error("更新任务运行状态失败，RunID: {}", taskRunId, e);
        }
    }

    /**
     * 触发重试逻辑
     */
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

            log.info("定时任务失败/超时，准备重试。TaskID: {}, 下次重试次数: {}, 间隔: {}秒", 
                    task.getId(), nextRetryNum, intervalSeconds);

            taskService.publishTaskWithRetryAndDelay(task.getId(), nextRetryNum, intervalSeconds);
        }
    }

    /**
     * 获取脚本的根目录路径
     * 如果脚本有父目录，则返回父目录的路径；否则返回脚本所在的目录路径
     */
    private String getRootPath(Script script) {
        if (script == null) {
            return null;
        }
        
        // 如果是目录类型，根目录就是自己的路径
        if (Boolean.TRUE.equals(script.getIsDirectory())) {
            return script.getPath();
        }
        
        // 如果有父目录ID，需要查询父目录的路径
        if (script.getParentId() != null) {
            Script parentScript = scriptService.getById(script.getParentId());
            if (parentScript != null) {
                return parentScript.getPath();
            }
        }
        
        // 否则返回脚本所在的目录路径（去掉文件名部分）
        String scriptPath = script.getPath();
        int lastSlash = scriptPath.lastIndexOf('/');
        if (lastSlash > 0) {
            return scriptPath.substring(0, lastSlash);
        }
        
        // 没有目录，返回根路径
        return "/";
    }
}
