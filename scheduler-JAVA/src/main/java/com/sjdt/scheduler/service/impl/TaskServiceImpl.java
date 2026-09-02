package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.sjdt.scheduler.service.TaskDispatchService;
import com.sjdt.scheduler.task.TaskCallbackExecutor;
import com.sjdt.scheduler.util.CronNextTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Task Service 实现类
 * 提供 Task 任务的管理和操作的具体实现
 */
@Slf4j
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

    /** 统一派发服务（路由选 Worker + Tag 定向投递） */
    private final TaskDispatchService taskDispatchService;

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

    private final ObjectMapper objectMapper;

    @Autowired
    public TaskServiceImpl(TaskDispatchService taskDispatchService,
                          IScriptService scriptService,
                          ScriptMapper scriptMapper,
                          TaskRunMapper taskRunMapper,
                          CallbackJobConfigMapper callbackJobConfigMapper,
                          ICallbackJobConfigService callbackJobConfigService,
                          TaskCallbackExecutor taskCallbackExecutor,
                          ObjectMapper objectMapper) {
        this.taskDispatchService = taskDispatchService;
        this.scriptService = scriptService;
        this.scriptMapper = scriptMapper;
        this.taskRunMapper = taskRunMapper;
        this.callbackJobConfigMapper = callbackJobConfigMapper;
        this.callbackJobConfigService = callbackJobConfigService;
        this.taskCallbackExecutor = taskCallbackExecutor;
        this.objectMapper = objectMapper;
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
        log.info("Worker离线，批量清理关联任务的TargetWorker | WorkerID: {}", workerId);

        this.lambdaUpdate()
                .eq(Task::getTargetWorker, workerId)
                .set(Task::getTargetWorker, null)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTask(TaskCreateDTO taskCreateDTO) {
        log.info("开始创建任务，任务名称：{}", taskCreateDTO.getName());

        Task task = buildTaskFromDTO(taskCreateDTO);
        // 活跃 cron 任务必须落库 next_trigger_time，否则到期驱动扫描永远扫不到它
        fillNextTriggerTime(task);
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

        Task task = this.getById(taskUpdateDTO.getId());
        if (task == null) {
            throw new IllegalArgumentException("任务不存在，ID: " + taskUpdateDTO.getId());
        }

        if (taskUpdateDTO.getRequiredTags() != null) {
            try {
                String requiredTagsJson = objectMapper.writeValueAsString(taskUpdateDTO.getRequiredTags());
                task.setRequiredTags(requiredTagsJson);
            } catch (JsonProcessingException e) {
                log.error("序列化requiredTags失败", e);
            }
        }

        // 更新基本信息（排除createdBy和createTime，不排除status以允许状态更新）
        BeanUtils.copyProperties(taskUpdateDTO, task, "id", "createdBy", "createTime", "requiredTags");
        
        if (taskUpdateDTO.getStatus() != null) {
            task.setStatus(taskUpdateDTO.getStatus());
        }

        // cron 表达式或状态变更后，重新计算下次触发时间（恢复/改 cron 都对齐到未来）
        fillNextTriggerTime(task);

        task.setUpdateTime(LocalDateTime.now());
        this.updateById(task);

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
            BeanUtils.copyProperties(configDTO, config, "id", "taskId", "createdBy", "createTime");
            config.setUpdateTime(LocalDateTime.now());
            callbackJobConfigMapper.updateById(config);
            log.info("回调配置更新成功，配置ID：{}", config.getId());
        } else {
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

        if (task.getRequiredTags() != null && !task.getRequiredTags().isEmpty()) {
            try {
                taskVO.setRequiredTags(objectMapper.readValue(task.getRequiredTags(), new TypeReference<java.util.Set<String>>() {}));
            } catch (JsonProcessingException e) {
                log.error("反序列化requiredTags失败，JSON: {}", task.getRequiredTags(), e);
            }
        }

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

    /**
     * 从DTO构建Task实体
     *
     * @param dto 任务创建DTO
     * @return Task实体
     */
    private Task buildTaskFromDTO(TaskCreateDTO dto) {
        String requiredTagsJson = null;
        if (dto.getRequiredTags() != null && !dto.getRequiredTags().isEmpty()) {
            try {
                requiredTagsJson = objectMapper.writeValueAsString(dto.getRequiredTags());
            } catch (JsonProcessingException e) {
                log.error("序列化requiredTags失败", e);
            }
        }

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
                .routeStrategy(dto.getRouteStrategy())
                .blockStrategy(dto.getBlockStrategy())
                .shardTotal(dto.getShardTotal())
                .requiredTags(requiredTagsJson)
                .status(TaskStatus.active)
                .createdBy("system")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * 为活跃 cron 任务计算并回填 next_trigger_time
     * <p>
     * 到期驱动扫描以 next_trigger_time 索引为准；新建/更新 cron 任务时必须落库下次触发时间，
     * 否则该任务要等重启（回填 Runner）才会被扫描到。updateById 默认只更新非 NULL 字段，
     * 因此非 cron / 非 active 任务不会被误写。
     */
    private void fillNextTriggerTime(Task task) {
        if (task.getTaskType() != TaskType.cron || task.getStatus() != TaskStatus.active) {
            return;
        }
        if (task.getCronExpr() == null || task.getCronExpr().isEmpty()) {
            log.warn("cron任务{}缺少cron表达式，无法计算下次触发时间", task.getId());
            return;
        }
        try {
            task.setNextTriggerTime(CronNextTimeUtil.next(LocalDateTime.now(), task.getCronExpr()));
        } catch (Exception e) {
            log.error("计算任务{}的next_trigger_time失败，cron: {}", task.getId(), task.getCronExpr(), e);
        }
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
     * 无分片身份，按任务自身配置决定是否分片
     */
    @Override
    public void publishTaskWithRetryAndDelay(String taskId, int retryNum, int delaySeconds) {
        publishTaskWithRetryAndDelay(taskId, retryNum, delaySeconds, null, null, null);
    }

    /**
     * 发布任务（支持指定重试次数、延时与分片身份）
     * 分片身份非空时为"单片重试"：新 run 沿用原片身份，重试仍处理同一段数据
     */
    @Override
    public void publishTaskWithRetryAndDelay(String taskId, int retryNum, int delaySeconds,
                                             Integer shardIndex, Integer shardTotal, String shardBatchId) {
        Task task = this.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("Task not found with id: " + taskId);
        }

        if (task.getExecuteType() == ExecuteType.script) {
            publishScriptTask(task, retryNum, delaySeconds, shardIndex, shardTotal, shardBatchId);
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
        publishScriptTask(task, 0, 0, null, null, null);
    }

    private void publishScriptTask(Task task, int retryNum, int delaySeconds) {
        publishScriptTask(task, retryNum, delaySeconds, null, null, null);
    }

    /**
     * 发布脚本任务（支持分片）
     * <ul>
     *     <li>分片任务新触发（无原片身份且 shardTotal &gt; 0）：N 片派发，每片一条 run + 一条定向消息；</li>
     *     <li>单片重试（shardBatchId 非空）：新 run 沿用原片身份，不换片（保证同段数据不重不漏）；</li>
     *     <li>非分片任务：单 run，按 N=1 特例写分片字段。</li>
     * </ul>
     *
     * @param task 任务对象
     * @param retryNum 重试次数
     * @param delaySeconds 延时秒数
     * @param shardIndex 原片分片序号（单片重试时非空）
     * @param shardTotal 原片分片总数（单片重试时非空）
     * @param shardBatchId 原片分片批次ID（单片重试时非空）
     */
    private void publishScriptTask(Task task, int retryNum, int delaySeconds,
                                   Integer shardIndex, Integer shardTotal, String shardBatchId) {
        Script script = scriptService.getById(task.getScriptId());
        if (script == null) {
            throw new IllegalArgumentException("Script not found with id: " + task.getScriptId());
        }

        String rootPath = getRootPath(script);
        log.info("脚本信息 - ID: {}, name: {}, path: {}, isDirectory: {}, parentId: {}, rootPath: {}",
                script.getId(), script.getName(), script.getPath(), script.getIsDirectory(), script.getParentId(), rootPath);

        // 路由选择统一交由 TaskDispatchService：requiredTags 过滤 + 路由策略选 Worker + Tag 定向投递。
        // 消息里的 targetWorkerId 仅作信息字段，不再承担路由职责。
        String targetWorkerId = task.getTargetWorker();

        TaskExecuteMessage.ScriptInfo scriptInfo = TaskExecuteMessage.ScriptInfo.builder()
                .id(script.getId())
                .name(script.getName())
                .path(script.getPath())
                .isDirectory(script.getIsDirectory())
                .mainFile(script.getMainFile())
                .parentId(script.getParentId())
                .rootPath(rootPath)
                .tags(script.getTags())
                .build();

        // 分片任务新触发（非单片重试）-> 一次触发生成 N 片 run + N 条定向消息
        Integer taskShardTotal = task.getShardTotal();
        boolean isShardRetry = shardBatchId != null && shardIndex != null;
        if (!isShardRetry && taskShardTotal != null && taskShardTotal > 0) {
            TaskExecuteMessage template = TaskExecuteMessage.builder()
                    .taskId(task.getId())
                    .taskName(task.getName())
                    .description(task.getDescription())
                    .scriptType(task.getScriptType().name())
                    .script(scriptInfo)
                    .taskType(task.getTaskType().name())
                    .cronExpr(task.getCronExpr())
                    .targetWorkerId(targetWorkerId)
                    .timeoutSeconds(task.getTimeoutSeconds())
                    .build();
            taskDispatchService.dispatchSharded(task, template, taskShardTotal, retryNum, delaySeconds);
            return;
        }

        // 单 run 路径：N=1 特例（独立批次ID）或单片重试（沿用原片身份）
        TaskRun taskRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .startTime(LocalDateTime.now())
                .retryNum(retryNum)
                .createTime(LocalDateTime.now())
                .shardIndex(isShardRetry ? shardIndex : 0)
                .shardTotal(isShardRetry ? shardTotal : 1)
                .shardBatchId(isShardRetry ? shardBatchId
                        : UUID.randomUUID().toString().replace("-", ""))
                .build();
        taskRunMapper.insert(taskRun);
        String taskRunId = taskRun.getId();
        log.info("运行实例已插入， 开始时间为{} id为{}", taskRun.getStartTime(), taskRunId);

        TaskExecuteMessage message = TaskExecuteMessage.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskRunId(taskRunId)
                .description(task.getDescription())
                .scriptType(task.getScriptType().name())
                .script(scriptInfo)
                .taskType(task.getTaskType().name())
                .cronExpr(task.getCronExpr())
                .targetWorkerId(targetWorkerId)
                .timeoutSeconds(task.getTimeoutSeconds())
                .shardIndex(taskRun.getShardIndex())
                .shardTotal(taskRun.getShardTotal())
                .shardBatchId(taskRun.getShardBatchId())
                .build();

        // 统一派发入口：路由选 Worker + Tag 定向投递；无可用 Worker 则 park（run 保持 pending）
        boolean dispatched = taskDispatchService.dispatch(task, message, delaySeconds);
        if (dispatched) {
            log.info("脚本任务已定向发布，任务ID: {}, 运行ID: {}, 重试次数: {}, 延时: {}s, 分片: {}/{}",
                    task.getId(), taskRunId, retryNum, delaySeconds,
                    taskRun.getShardIndex(), taskRun.getShardTotal());
        } else {
            log.warn("脚本任务无可用Worker，已park，任务ID: {}, 运行ID: {}（交派发超时监控兜底）",
                    task.getId(), taskRunId);
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
        // 回调任务当前由调度器同步执行；延时重试统一由 TaskResultConsumer 异步触发，不走 MQ 延时消息
        if (delaySeconds > 0) {
        }

        CallbackJobConfig config = getCallbackConfigByTaskId(task.getId());

        TaskRun taskRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .retryNum(retryNum)
                .createTime(LocalDateTime.now())
                // 回调任务不分片，统一按 N=1 特例写分片字段，与分片结算路径保持一致
                .shardIndex(0)
                .shardTotal(1)
                .shardBatchId(UUID.randomUUID().toString().replace("-", ""))
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

        // 重置计时基准：超时重发视为一次新的执行尝试
        taskRun.setStartTime(LocalDateTime.now());
        taskRun.setLastHeartbeatTime(LocalDateTime.now());
        taskRunMapper.updateById(taskRun);

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
                    .isDirectory(script.getIsDirectory())
                    .mainFile(script.getMainFile())
                    .parentId(script.getParentId())
                    .rootPath(getRootPath(script))
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
                    // 沿用原 run 的分片身份：超时重发不换片，仍处理同一段数据
                    .shardIndex(taskRun.getShardIndex() != null ? taskRun.getShardIndex() : 0)
                    .shardTotal(taskRun.getShardTotal() != null ? taskRun.getShardTotal() : 1)
                    .shardBatchId(taskRun.getShardBatchId())
                    .build();

            // 统一派发入口：路由选 Worker + Tag 定向投递；无可用 Worker 则 park（run 保持 pending）
            boolean dispatched = taskDispatchService.dispatch(task, message, delaySeconds);
            if (dispatched) {
                log.info("脚本任务已重新发布(超时重试)，TaskRunID: {}, 延时: {}s", taskRunId, delaySeconds);
            } else {
                log.warn("脚本任务重试无可用Worker，已park，TaskRunID: {}（交派发超时监控兜底）", taskRunId);
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

    /**
     * 获取脚本的根目录路径
     * 如果脚本有父目录，则返回父目录的路径；否则返回脚本所在的目录路径
     */
    private String getRootPath(Script script) {
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
        
        return "/";
    }
}
