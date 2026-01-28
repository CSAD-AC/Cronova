package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.domain.dto.CallbackJobConfigCreateDTO;
import com.sjdt.scheduler.domain.dto.CallbackJobConfigUpdateDTO;
import com.sjdt.scheduler.domain.dto.TaskCreateDTO;
import com.sjdt.scheduler.domain.dto.TaskUpdateDTO;
import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.domain.vo.CallbackJobConfigVO;
import com.sjdt.scheduler.domain.vo.TaskVO;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public TaskServiceImpl(TaskProducerService taskProducerService,
                          IScriptService scriptService,
                          ScriptMapper scriptMapper,
                          TaskRunMapper taskRunMapper,
                          CallbackJobConfigMapper callbackJobConfigMapper,
                          ICallbackJobConfigService callbackJobConfigService,
                          TaskCallbackExecutor taskCallbackExecutor,
                          RedisTemplate<String, Object> redisTemplate) {
        this.taskProducerService = taskProducerService;
        this.scriptService = scriptService;
        this.scriptMapper = scriptMapper;
        this.taskRunMapper = taskRunMapper;
        this.callbackJobConfigMapper = callbackJobConfigMapper;
        this.callbackJobConfigService = callbackJobConfigService;
        this.taskCallbackExecutor = taskCallbackExecutor;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 启动时清理任务缓存，防止由于序列化格式变更导致的 ClassCastException
     */
    @PostConstruct
    public void init() {
        log.info("系统启动，清理任务活跃状态缓存以防止序列化异常");
        redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);
    }

    /**
     * 获取所有活跃的定时任务
     */
    @Override
    public List<Task> listActiveCronTasks() {
        return this.list(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                .eq(Task::getTaskType, TaskType.cron)
                .eq(Task::getStatus, TaskStatus.active)
        );
    }

    /**
     * 根据 Worker ID 更新任务的 target_worker 为 null
     *
     * @param workerId Worker ID
     */
    @Override
    public void updateTargetWorkerToNullByWorkerId(String workerId) {
        log.info("Worker离线，批量清理关联任务的TargetWorker并失效任务缓存 | WorkerID: {}", workerId);
        // 清理 Redis 缓存（手动清理）
        redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);
        
        this.lambdaUpdate()
                .eq(Task::getTargetWorker, workerId)
                .set(Task::getTargetWorker, null)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTask(TaskCreateDTO taskCreateDTO) {
        log.info("开始创建任务，任务名称：{}", taskCreateDTO.getName());
        
        // 清理缓存
        redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);

        Task task = buildTaskFromDTO(taskCreateDTO);
        this.save(task);
        log.info("任务创建成功，任务ID：{}", task.getId());

        if (taskCreateDTO.getExecuteType() == ExecuteType.callback) {
            createCallbackConfig(task, taskCreateDTO.getCallbackConfig());
        }

        return task.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTask(TaskUpdateDTO taskUpdateDTO) {
        log.info("开始更新任务，任务ID：{}", taskUpdateDTO.getId());
        
        // 清理缓存
        redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);

        Task task = this.getById(taskUpdateDTO.getId());
        if (task == null) {
            throw new IllegalArgumentException("任务不存在，ID: " + taskUpdateDTO.getId());
        }

        // 更新基本信息
        BeanUtils.copyProperties(taskUpdateDTO, task, "id", "status", "createdBy", "createTime");
        task.setUpdateTime(LocalDateTime.now());
        this.updateById(task);

        // 处理回调配置
        if (task.getExecuteType() == ExecuteType.callback && taskUpdateDTO.getCallbackConfig() != null) {
            updateOrCreateCallbackConfig(task, taskUpdateDTO.getCallbackConfig());
        }
        
        log.info("任务及其配置更新完成，任务ID：{}", task.getId());
    }

    private void updateOrCreateCallbackConfig(Task task, CallbackJobConfigUpdateDTO configDTO) {
        CallbackJobConfig config = callbackJobConfigMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                        .eq(CallbackJobConfig::getTaskId, task.getId())
        );

        if (config != null) {
            // 更新现有配置
            BeanUtils.copyProperties(configDTO, config, "id", "taskId", "createdBy", "createTime");
            config.setUpdateTime(LocalDateTime.now());
            callbackJobConfigMapper.updateById(config);
            log.info("回调配置更新成功，配置ID：{}", config.getId());
        } else {
            // 创建新配置（如果之前没有）
            CallbackJobConfig newConfig = CallbackJobConfig.builder()
                    .taskId(task.getId())
                    .callbackUrl(configDTO.getCallbackUrl())
                    .callbackMethod(configDTO.getCallbackMethod() != null ? configDTO.getCallbackMethod() : "POST")
                    .secretKey(configDTO.getSecretKey())
                    .createdBy("system")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            callbackJobConfigMapper.insert(newConfig);
            log.info("回调配置补全创建成功，配置ID：{}", newConfig.getId());
        }
    }

    @Override
    public TaskVO getTaskVO(String id) {
        Task task = this.getById(id);
        if (task == null) {
            return null;
        }
        return buildTaskVO(task);
    }

    @Override
    public List<TaskVO> listTaskVOs() {
        List<Task> tasks = this.list(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                .ne(Task::getStatus, TaskStatus.deleted)
        );
        return tasks.stream().map(this::buildTaskVO).collect(Collectors.toList());
    }

    @Override
    public List<TaskVO> listRecentTasks(int limit) {
        List<Task> tasks = this.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Task>()
                        .ne(Task::getStatus, TaskStatus.deleted)
                        .orderByDesc(Task::getCreateTime)
                        .last("LIMIT " + limit)
        );
        return tasks.stream().map(this::buildTaskVO).collect(Collectors.toList());
    }

    /**
     * 构建TaskVO，包含回调配置信息
     */
    private TaskVO buildTaskVO(Task task) {
        TaskVO taskVO = new TaskVO();
        BeanUtils.copyProperties(task, taskVO);

        if (task.getExecuteType() == ExecuteType.callback) {
            CallbackJobConfig config = callbackJobConfigMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                            .eq(CallbackJobConfig::getTaskId, task.getId())
            );
            if (config != null) {
                CallbackJobConfigVO configVO = new CallbackJobConfigVO();
                BeanUtils.copyProperties(config, configVO);
                taskVO.setCallbackConfig(configVO);
            }
        }

        return taskVO;
    }

    @Override
    public boolean updateById(Task entity) {
        redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);
        return super.updateById(entity);
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);
        return super.removeById(id);
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
                .timeoutSeconds(dto.getTimeoutSeconds() != null ? dto.getTimeoutSeconds() : 180)
                .retryCount(dto.getRetryCount() != null ? dto.getRetryCount() : 0)
                .retryIntervalSeconds(dto.getRetryIntervalSeconds() != null ? dto.getRetryIntervalSeconds() : 60)
                .targetWorker(dto.getTargetWorker())
                .status(TaskStatus.active)
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
        publishTaskWithRetryAndDelay(taskId, 0, 0);
    }
    
    /**
     * 发布任务（支持指定重试次数和延时）
     */
    @Override
    public void publishTaskWithRetryAndDelay(String taskId, int retryNum, int delaySeconds) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found with id: " + taskId);
        }

        if (task.getExecuteType() == ExecuteType.script) {
            publishScriptTask(task, retryNum, delaySeconds);
        } else if (task.getExecuteType() == ExecuteType.callback) {
            publishCallbackTask(task, retryNum, delaySeconds);
        }
    }

    /**
     * 发布脚本任务
     * 发送到RocketMQ，由Worker执行
     *
     * @param task 任务对象
     */
    private void publishScriptTask(Task task) {
        publishScriptTask(task, 0, 0);
    }

    private void publishScriptTask(Task task, int retryNum, int delaySeconds) {
        Script script = scriptService.getById(task.getScriptId());
        if (script == null) {
            throw new IllegalArgumentException("Script not found with id: " + task.getScriptId());
        }

        TaskRun taskRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .startTime(LocalDateTime.now())
                .retryNum(retryNum)
                .createTime(LocalDateTime.now())
                .build();
        taskRunMapper.insert(taskRun);
        String taskRunId = taskRun.getId();
        log.warn("运行实例已插入， 开始时间为{} id为{}", taskRun.getStartTime(), taskRunId);

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

        if (delaySeconds > 0) {
            int delayLevel = com.sjdt.scheduler.util.RocketMQDelayUtil.getDelayLevel(delaySeconds);
            taskProducerService.sendTaskWithDelay(message, delayLevel);
            log.info("脚本延时任务已发布，任务ID: {}, 运行ID: {}, 重试次数: {}, 延时: {}s (Level: {})", 
                    task.getId(), taskRunId, retryNum, delaySeconds, delayLevel);
        } else {
            taskProducerService.sendTask(message);
            log.info("脚本任务已发布，任务ID: {}, 运行ID: {}, 重试次数: {}", task.getId(), taskRunId, retryNum);
        }
    }

    /**
     * 发布回调任务
     * 直接执行HTTP回调请求
     *
     * @param task 任务对象
     */
    private void publishCallbackTask(Task task) {
        publishCallbackTask(task, 0, 0);
    }

    private void publishCallbackTask(Task task, int retryNum, int delaySeconds) {
        // 如果有延时，通过 MQ 延时消息发送给自己（模拟延时）
        // 但目前系统回调是直接由调度器执行的，所以如果要支持延时重试，
        // 最好是发送一个延时消息到 MQ，然后由另一个消费者处理
        // 为了保持简单且符合当前架构，我们检查 delaySeconds
        if (delaySeconds > 0) {
            // 这里我们需要一个专门处理延时回调的消息模型和消费者
            // 或者直接让 publishTaskWithRetryAndDelay 被 MQ 消费者调用
            // 目前为了快速实现且不引入太多新组件，我们在 TaskResultConsumer 中异步处理
            // 如果用户要求使用 MQ 延时消息，我们需要确保所有重试都走 MQ
            // 脚本任务已经走 MQ 了，回调任务目前是同步执行
            
            // 如果回调任务也想利用 MQ 的延时能力，我们需要发送消息到 MQ
            // 暂且认为用户希望脚本任务利用 MQ 延时，回调任务可以保持现状或也改造成 MQ 驱动
            // 为了统一，我们保持目前的 publishCallbackTask 逻辑，但如果需要重试延时，
            // 可以在 publishCallbackTask 内部实现异步或者让 TaskResultConsumer 触发。
            
            // 这里我们采取折中方案：回调任务的重试如果也需要延时，暂时在 TaskResultConsumer 异步处理
            // 而脚本任务则利用 MQ 的原生延时级别。
        }

        CallbackJobConfig config = getCallbackConfigByTaskId(task.getId());

        TaskRun taskRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .retryNum(retryNum)
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
                .timeoutSeconds(task.getTimeoutSeconds())
                .retryCount(task.getRetryCount())
                .retryIntervalSeconds(task.getRetryIntervalSeconds())
                .triggerTime(LocalDateTime.now())
                .build();

        taskCallbackExecutor.executeCallbackJob(message);
        log.info("回调任务已发布，任务ID: {}, 运行ID: {}, URL: {}, 重试次数: {}", task.getId(), taskRunId, config.getCallbackUrl(), retryNum);
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

    @Override
    public void republishTaskRun(String taskRunId, int delaySeconds) {
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.error("重新发布任务失败，TaskRun不存在: {}", taskRunId);
            return;
        }

        Task task = this.getById(taskRun.getTaskId());
        if (task == null) {
            log.error("重新发布任务失败，Task不存在: {}", taskRun.getTaskId());
            return;
        }

        if (task.getExecuteType() == ExecuteType.script) {
            Script script = scriptService.getById(task.getScriptId());
            if (script == null) {
                log.error("重新发布任务失败，Script不存在: {}", task.getScriptId());
                return;
            }

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

            if (delaySeconds > 0) {
                int delayLevel = com.sjdt.scheduler.util.RocketMQDelayUtil.getDelayLevel(delaySeconds);
                taskProducerService.sendTaskWithDelay(message, delayLevel);
                log.info("脚本任务已重新发布(超时重试)，TaskRunID: {}, 延时: {}s", taskRunId, delaySeconds);
            } else {
                taskProducerService.sendTask(message);
                log.info("脚本任务已重新发布(超时重试)，TaskRunID: {}", taskRunId);
            }

        } else if (task.getExecuteType() == ExecuteType.callback) {
            CallbackJobConfig config = getCallbackConfigByTaskId(task.getId());

            com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage message = com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage.builder()
                    .taskId(task.getId())
                    .taskRunId(taskRunId)
                    .configId(config.getId())
                    .callbackUrl(config.getCallbackUrl())
                    .callbackMethod(config.getCallbackMethod())
                    .secretKey(config.getSecretKey())
                    .timeoutSeconds(task.getTimeoutSeconds())
                    .retryCount(task.getRetryCount())
                    .retryIntervalSeconds(task.getRetryIntervalSeconds())
                    .triggerTime(LocalDateTime.now())
                    .build();

            taskCallbackExecutor.executeCallbackJob(message);
            log.info("回调任务已重新发布(超时重试)，TaskRunID: {}", taskRunId);
        }
    }
}
