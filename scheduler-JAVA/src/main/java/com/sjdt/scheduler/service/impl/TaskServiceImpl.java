package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.dto.CallbackJobConfigCreateDTO;
import com.sjdt.scheduler.domain.dto.TaskCreateDTO;
import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.ICallbackJobConfigService;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.TaskProducerService;
import com.sjdt.scheduler.task.TaskCallbackExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Task Service 实现类
 * 提供 Task 任务的管理和操作的具体实现
 */
@Slf4j
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

    /** 任务生产者服务 */
    private final TaskProducerService taskProducerService;

    /** 脚本服务 */
    private final IScriptService scriptService;

    /** 脚本Mapper */
    private final ScriptMapper scriptMapper;

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;

    /** 回调配置Mapper */
    private final CallbackJobConfigMapper callbackJobConfigMapper;

    /** 回调配置服务 */
    private final ICallbackJobConfigService callbackJobConfigService;

    /** 回调任务执行器 */
    private final TaskCallbackExecutor taskCallbackExecutor;

    @Autowired
    public TaskServiceImpl(TaskProducerService taskProducerService,
                          IScriptService scriptService,
                          ScriptMapper scriptMapper,
                          TaskRunMapper taskRunMapper,
                          CallbackJobConfigMapper callbackJobConfigMapper,
                          ICallbackJobConfigService callbackJobConfigService,
                          TaskCallbackExecutor taskCallbackExecutor) {
        this.taskProducerService = taskProducerService;
        this.scriptService = scriptService;
        this.scriptMapper = scriptMapper;
        this.taskRunMapper = taskRunMapper;
        this.callbackJobConfigMapper = callbackJobConfigMapper;
        this.callbackJobConfigService = callbackJobConfigService;
        this.taskCallbackExecutor = taskCallbackExecutor;
    }

    /**
     * 根据 Worker ID 更新任务的 target_worker 为 null
     *
     * @param workerId Worker ID
     */
    @Override
    public void updateTargetWorkerToNullByWorkerId(String workerId) {
        this.lambdaUpdate()
                .eq(Task::getTargetWorker, workerId)
                .set(Task::getTargetWorker, null)
                .update();
    }

    /**
     * 创建任务
     * 支持一次性创建任务和回调配置（如果是callback类型）
     *
     * @param taskCreateDTO 任务创建DTO
     * @return 创建的任务ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createTask(TaskCreateDTO taskCreateDTO) {
        log.info("开始创建任务，任务名称：{}", taskCreateDTO.getName());

        Task task = buildTaskFromDTO(taskCreateDTO);
        this.save(task);
        log.info("任务创建成功，任务ID：{}", task.getId());

        if (taskCreateDTO.getExecuteType() == ExecuteType.callback) {
            createCallbackConfig(task, taskCreateDTO.getCallbackConfig());
        }

        return task.getId();
    }

    /**
     * 从DTO构建Task实体
     *
     * @param dto 任务创建DTO
     * @return Task实体
     */
    private Task buildTaskFromDTO(TaskCreateDTO dto) {
        return Task.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .executeType(dto.getExecuteType())
                .scriptType(dto.getScriptType())
                .scriptId(dto.getScriptId())
                .taskType(dto.getTaskType())
                .cronExpr(dto.getCronExpr())
                .enabled(true)
                .timeoutSeconds(dto.getTimeoutSeconds() != null ? dto.getTimeoutSeconds() : 0)
                .targetWorker(dto.getTargetWorker())
                .status(com.sjdt.scheduler.domain.enums.TaskStatus.active)
                .createdBy("system")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 创建回调配置
     *
     * @param task 任务实体
     * @param configDTO 回调配置DTO
     */
    private void createCallbackConfig(Task task, CallbackJobConfigCreateDTO configDTO) {
        if (configDTO == null) {
            throw new IllegalArgumentException("回调类型任务必须提供回调配置");
        }

        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(task.getId())
                .callbackUrl(configDTO.getCallbackUrl())
                .callbackMethod(configDTO.getCallbackMethod() != null ? configDTO.getCallbackMethod() : "POST")
                .secretKey(configDTO.getSecretKey())
                .timeoutSeconds(configDTO.getTimeoutSeconds() != null ? configDTO.getTimeoutSeconds() : 30)
                .retryCount(configDTO.getRetryCount() != null ? configDTO.getRetryCount() : 0)
                .retryIntervalSeconds(configDTO.getRetryIntervalSeconds() != null ? configDTO.getRetryIntervalSeconds() : 60)
                .createdBy("system")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        callbackJobConfigMapper.insert(config);
        log.info("回调配置创建成功，配置ID：{}，关联任务ID：{}", config.getId(), task.getId());
    }

    /**
     * 发布任务
     * 根据任务执行类型分发到不同的执行器
     *
     * @param taskId 任务ID
     */
    @Override
    public void publishTask(String taskId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found with id: " + taskId);
        }

        if (task.getExecuteType() == ExecuteType.script) {
            publishScriptTask(task);
        } else if (task.getExecuteType() == ExecuteType.callback) {
            publishCallbackTask(task);
        }
    }

    /**
     * 发布脚本任务
     * 发送到RocketMQ，由Worker执行
     *
     * @param task 任务对象
     */
    private void publishScriptTask(Task task) {
        Script script = scriptMapper.selectById(task.getScriptId());
        if (script == null) {
            throw new IllegalArgumentException("Script not found with id: " + task.getScriptId());
        }

        TaskRun taskRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .createTime(LocalDateTime.now())
                .build();
        taskRunMapper.insert(taskRun);
        String taskRunId = taskRun.getId();

        TaskExecuteMessage.ScriptInfo scriptInfo = TaskExecuteMessage.ScriptInfo.builder()
                .id(script.getId())
                .name(script.getName())
                .path(script.getPath())
                .tags(script.getTags())
                .build();

        TaskExecuteMessage message = TaskExecuteMessage.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskRunId(taskRunId)
                .description(task.getDescription())
                .scriptType(task.getScriptType().name())
                .script(scriptInfo)
                .taskType(task.getTaskType().name())
                .cronExpr(task.getCronExpr())
                .targetWorkerId(task.getTargetWorker())
                .timeoutSeconds(task.getTimeoutSeconds())
                .build();

        taskProducerService.sendTask(message);
        log.info("脚本任务已发布，任务ID: {}, 运行ID: {}", task.getId(), taskRunId);
    }

    /**
     * 发布回调任务
     * 直接执行HTTP回调请求
     *
     * @param task 任务对象
     */
    private void publishCallbackTask(Task task) {
        CallbackJobConfig config = getCallbackConfigByTaskId(task.getId());

        TaskRun taskRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .createTime(LocalDateTime.now())
                .build();
        taskRunMapper.insert(taskRun);
        String taskRunId = taskRun.getId();

        com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage message = com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage.builder()
                .taskId(task.getId())
                .taskRunId(taskRunId)
                .configId(config.getId())
                .callbackUrl(config.getCallbackUrl())
                .callbackMethod(config.getCallbackMethod())
                .secretKey(config.getSecretKey())
                .timeoutSeconds(config.getTimeoutSeconds())
                .retryCount(config.getRetryCount())
                .retryIntervalSeconds(config.getRetryIntervalSeconds())
                .triggerTime(LocalDateTime.now())
                .build();

        taskCallbackExecutor.executeCallbackJob(message);
        log.info("回调任务已发布，任务ID: {}, 运行ID: {}, URL: {}", task.getId(), taskRunId, config.getCallbackUrl());
    }

    /**
     * 根据任务ID获取回调配置
     *
     * @param taskId 任务ID
     * @return 回调配置
     */
    private CallbackJobConfig getCallbackConfigByTaskId(String taskId) {
        return callbackJobConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                        .eq(CallbackJobConfig::getTaskId, taskId)
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("回调配置不存在，任务ID: " + taskId));
    }

    /**
     * 发布立即执行的任务
     * 用于一次性任务的立即执行
     *
     * @param task 任务对象
     */
    @Override
    public void publishImmediateTask(Task task) {
        if (task.getExecuteType() == ExecuteType.script) {
            publishScriptTask(task);
        } else if (task.getExecuteType() == ExecuteType.callback) {
            publishCallbackTask(task);
        }
    }
}
