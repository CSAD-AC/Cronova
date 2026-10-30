package com.cronova.scheduler.controller;

import cn.hutool.core.util.IdUtil;
import com.cronova.scheduler.common.ErrorCodeEnum;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.message.WorkerRegisterDTO;
import com.cronova.scheduler.domain.message.UpdateTagsRequest;
import com.cronova.scheduler.domain.enums.WorkerStatus;
import com.cronova.scheduler.domain.po.AKSK;
import com.cronova.scheduler.domain.po.Tag;
import com.cronova.scheduler.domain.po.TaskLog;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.domain.po.Worker;
import com.cronova.scheduler.domain.po.WorkerTag;
import com.cronova.scheduler.domain.vo.WorkerRegisterVO;
import com.cronova.scheduler.mapper.AKSKMapper;
import com.cronova.scheduler.mapper.TagMapper;
import com.cronova.scheduler.mapper.TaskLogMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.mapper.WorkerMapper;
import com.cronova.scheduler.mapper.WorkerTagMapper;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.TaskResultMessage;
import com.cronova.scheduler.service.IAKSKService;
import com.cronova.scheduler.service.ITaskService;
import com.cronova.scheduler.service.ShardSettlementService;
import com.cronova.scheduler.service.TaskRunFinalizer;
import com.cronova.scheduler.common.RedisKey;
import com.cronova.scheduler.interceptor.AKSKInterceptor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Worker控制器
 * 处理Worker执行器的注册、状态报告、心跳等请求
 * 注意：此控制器仅处理Worker执行器到调度器的通信，不处理前端请求
 */
@RestController
@RequestMapping("/api/worker")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Worker执行器API", description = "处理Worker执行器的注册、心跳、状态报告等请求")
@Slf4j
public class WorkerController {

    private final WorkerMapper workerMapper;
    private final AKSKMapper akskMapper;
    private final WorkerTagMapper workerTagMapper;
    private final TagMapper tagMapper;
    private final TaskRunMapper taskRunMapper;
    private final TaskLogMapper taskLogMapper;
    private final ITaskService taskService;
    private final IAKSKService akskService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ShardSettlementService shardSettlementService;
    private final TaskRunFinalizer taskRunFinalizer;

    /** 注册请求头中携带的注册令牌字段名 */
    private static final String HEADER_REGISTER_TOKEN = "X-Register-Token";

    /** 全局 Worker 注册令牌（配置为空时拒绝注册，fail-closed） */
    @Value("${cronova.security.worker-register-token:}")
    private String workerRegisterToken;

    public WorkerController(WorkerMapper workerMapper, AKSKMapper akskMapper, WorkerTagMapper workerTagMapper,
                           TagMapper tagMapper, TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper,
                           ITaskService taskService, IAKSKService akskService, RedisTemplate<String, Object> redisTemplate,
                           ShardSettlementService shardSettlementService, TaskRunFinalizer taskRunFinalizer) {
        this.workerMapper = workerMapper;
        this.akskMapper = akskMapper;
        this.workerTagMapper = workerTagMapper;
        this.tagMapper = tagMapper;
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskService = taskService;
        this.akskService = akskService;
        this.redisTemplate = redisTemplate;
        this.shardSettlementService = shardSettlementService;
        this.taskRunFinalizer = taskRunFinalizer;
    }

    private String authenticatedWorkerId(HttpServletRequest request) {
        Object value = request.getAttribute(AKSKInterceptor.AUTHENTICATED_WORKER_ID);
        return value == null ? null : value.toString();
    }

    private boolean isOwnWorker(HttpServletRequest request, String workerId) {
        String authenticatedWorkerId = authenticatedWorkerId(request);
        return authenticatedWorkerId != null && authenticatedWorkerId.equals(workerId);
    }

    private Result<Void> workerMismatch() {
        return Result.error(403, "worker identity mismatch");
    }

    /**
     * Worker注册接口
     * 用于Worker首次启动时注册到调度器，获取AKSK
     */
    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    @Operation(summary = "Worker注册", description = "Worker执行器首次启动时注册到调度器，获取AKSK认证信息")
    public Result<WorkerRegisterVO> register(@RequestBody WorkerRegisterDTO registerDTO,
                                             @RequestHeader(value = HEADER_REGISTER_TOKEN, required = false) String registerToken) {
        log.info("收到Worker注册请求，主机名: {}, IP: {}", registerDTO.getHost(), registerDTO.getIp());

        // 注册令牌必须显式配置；空配置时 fail-closed，避免默认部署泄露 AKSK
        if (workerRegisterToken == null || workerRegisterToken.isBlank()) {
            log.error("Worker注册被拒绝：cronova.security.worker-register-token 未配置");
            return Result.error(503, "worker register token is not configured");
        }
        boolean tokenOk = registerToken != null && MessageDigest.isEqual(
                registerToken.getBytes(StandardCharsets.UTF_8),
                workerRegisterToken.getBytes(StandardCharsets.UTF_8));
        if (!tokenOk) {
            log.warn("Worker注册被拒绝：注册令牌无效，IP: {}", registerDTO.getIp());
            return Result.error(401, "invalid register token");
        }

        Worker existingWorker = workerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Worker>()
                        .eq(Worker::getHost, registerDTO.getHost())
                        .or()
                        .eq(Worker::getIp, registerDTO.getIp())
        );

        WorkerRegisterVO registerVO = new WorkerRegisterVO();

        if (existingWorker != null) {
            log.info("Worker已存在，ID: {}", existingWorker.getId());
            AKSK aksk = akskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AKSK>()
                            .eq(AKSK::getWorkerId, existingWorker.getId())
                            .eq(AKSK::getStatus, "active")
            );

            if (aksk == null) {
                log.warn("Worker存在但缺少 active AKSK，重新生成，WorkerID: {}", existingWorker.getId());
                aksk = akskService.generateAKSK(existingWorker.getId());
            }

            existingWorker.setStatus(WorkerStatus.online);
            existingWorker.setLastHeartbeat(LocalDateTime.now());
            workerMapper.updateById(existingWorker);

            // 清理可能存在的旧 AKSK 缓存，确保新下发的 AKSK 立即生效
            redisTemplate.delete(RedisKey.AKSK_CACHE + ":" + aksk.getAccessKey());

            registerVO.setWorkerId(existingWorker.getId());
            registerVO.setAccessKey(aksk.getAccessKey());
            registerVO.setSecretKey(aksk.getSecretKey());

            log.info("Worker注册成功，已存在Worker，返回现有Worker信息和AKSK");
            return Result.success(registerVO);
        }

        log.info("Worker不存在，创建新的Worker记录");
        Worker newWorker = new Worker();
        newWorker.setHost(registerDTO.getHost());
        newWorker.setIp(registerDTO.getIp());
        newWorker.setStatus(WorkerStatus.online);
        newWorker.setLastHeartbeat(LocalDateTime.now());
        newWorker.setCreateTime(LocalDateTime.now());
        newWorker.setUpdateTime(LocalDateTime.now());
        workerMapper.insert(newWorker);

        String accessKey = "AK" + IdUtil.fastSimpleUUID().toUpperCase();
        String secretKey = "SK" + IdUtil.fastSimpleUUID().toUpperCase();
        
        AKSK aksk = new AKSK();
        aksk.setAccessKey(accessKey);
        aksk.setSecretKey(secretKey);
        aksk.setWorkerId(newWorker.getId());
        aksk.setStatus("active");
        aksk.setCreateTime(LocalDateTime.now());
        aksk.setUpdateTime(LocalDateTime.now());
        akskMapper.insert(aksk);
        log.info("为新Worker生成AKSK，WorkerID: {}, AccessKey: {}", newWorker.getId(), aksk.getAccessKey());

        if (registerDTO.getTags() != null && !registerDTO.getTags().isEmpty()) {
            log.info("为新Worker处理标签，WorkerID: {}, 标签数量: {}", newWorker.getId(), registerDTO.getTags().size());
            for (String tagName : registerDTO.getTags()) {
                Tag tag = tagMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tag>()
                                .eq(Tag::getName, tagName)
                );

                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagName);
                    tag.setDescription("Worker注册时自动创建");
                    tag.setCreateTime(LocalDateTime.now());
                    tagMapper.insert(tag);
                    log.info("创建新标签，名称: {}, ID: {}", tag.getName(), tag.getId());
                }

                WorkerTag workerTag = new WorkerTag();
                workerTag.setWorkerId(newWorker.getId());
                workerTag.setTagId(tag.getId());
                workerTag.setCreateTime(LocalDateTime.now());
                workerTagMapper.insert(workerTag);
            }
        }

        registerVO.setWorkerId(newWorker.getId());
        registerVO.setAccessKey(aksk.getAccessKey());
        registerVO.setSecretKey(aksk.getSecretKey());

        log.info("Worker注册成功，创建新Worker记录，ID: {}", newWorker.getId());
        return Result.success(registerVO);
    }

    /**
     * Worker心跳接口
     */
    @PostMapping("/heartbeat/{workerId}")
    @Operation(summary = "Worker心跳", description = "Worker执行器定期发送心跳，保持在线状态")
    public Result<Void> heartbeat(@PathVariable String workerId, HttpServletRequest request) {
        log.debug("收到Worker心跳请求 | WorkerID: {}", workerId);
        if (!isOwnWorker(request, workerId)) {
            return workerMismatch();
        }
        
        String heartbeatKey = RedisKey.getWorkerHeartbeatKey(workerId);
        
        // 心跳键缺失说明 Worker 刚启动或从离线恢复，同步激活数据库状态为在线
        if (!redisTemplate.hasKey(heartbeatKey)) {
            log.info("检测到心跳键缺失，尝试激活 Worker 状态为在线 | WorkerID: {}", workerId);
            Worker worker = workerMapper.selectById(workerId);
            if (worker != null && worker.getStatus() != WorkerStatus.online) {
                worker.setStatus(WorkerStatus.online);
                worker.setLastHeartbeat(LocalDateTime.now());
                worker.setUpdateTime(LocalDateTime.now());
                workerMapper.updateById(worker);
                log.info("Worker 状态已通过心跳实时激活为在线 | WorkerID: {}", workerId);
            }
        }

        // 刷新心跳存活键，并更新 Hash 中的最后心跳时间。
        // TTL 收窄到 10s：配合 WorkerHeartbeatCheckTask(10s) 使 Worker 离线判定先于
        // TaskTimeoutMonitor 的 run 级 30s 心跳超时，保证孤儿 run 走主动故障转移而非被动判死。
        redisTemplate.opsForValue().set(heartbeatKey, "online", 10, TimeUnit.SECONDS);
        
        String infoKey = RedisKey.getWorkerInfoKey(workerId);
        redisTemplate.opsForHash().put(infoKey, "lastHeartbeat", LocalDateTime.now().toString());
        
        return Result.success();
    }

    /**
     * Worker状态报告接口
     */
    @PostMapping("/status/{workerId}")
    @Operation(summary = "Worker状态报告", description = "Worker执行器定期向调度器报告CPU、内存使用率和运行任务数")
    public Result<Void> reportStatus(@PathVariable String workerId, 
                                     @RequestParam Double cpuUsage, 
                                     @RequestParam Double memUsage, 
                                     @RequestParam Integer runningTasks, HttpServletRequest request) {
        log.debug("收到Worker状态报告 | WorkerID: {}, CPU: {}%, Mem: {}%, Tasks: {}", 
                workerId, cpuUsage, memUsage, runningTasks);
        if (!isOwnWorker(request, workerId)) {
            return workerMismatch();
        }
        
        String infoKey = RedisKey.getWorkerInfoKey(workerId);
        Map<String, Object> statusMap = new HashMap<>();
        statusMap.put("cpuUsage", cpuUsage);
        statusMap.put("memUsage", memUsage);
        statusMap.put("runningTasks", runningTasks);
        statusMap.put("lastHeartbeat", LocalDateTime.now().toString());
        
        // 写入 Redis Hash
        redisTemplate.opsForHash().putAll(infoKey, statusMap);
        // 同时刷新心跳存活时间（TTL 10s，与心跳接口一致，保证离线判定先于 run 心跳超时）
        redisTemplate.opsForValue().set(RedisKey.getWorkerHeartbeatKey(workerId), "online", 10, TimeUnit.SECONDS);
        
        return Result.success();
    }

    /**
     * Worker标签更新接口
     * Worker动态更新标签时调用此接口
     */
    @PostMapping("/tags/{workerId}")
    @Operation(summary = "更新Worker标签", description = "Worker执行器动态更新自身标签，用于任务匹配")
    public Result<Void> updateWorkerTags(@PathVariable String workerId, @RequestBody UpdateTagsRequest request, HttpServletRequest httpRequest) {
        log.info("收到Worker标签更新请求 | WorkerID: {}, 新标签: {}", workerId, request.getTags());
        if (!isOwnWorker(httpRequest, workerId)) {
            return workerMismatch();
        }

        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，无法更新标签 | WorkerID: {}", workerId);
            return Result.error(ErrorCodeEnum.WORKER_NOT_FOUND);
        }

        List<String> newTags = request.getTags();
        if (newTags == null || newTags.isEmpty()) {
            log.info("Worker标签为空，清空所有标签 | WorkerID: {}", workerId);
            workerTagMapper.delete(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkerTag>()
                            .eq(WorkerTag::getWorkerId, workerId)
            );
            return Result.success();
        }

        // 1. 删除旧的标签关联
        workerTagMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkerTag>()
                        .eq(WorkerTag::getWorkerId, workerId)
        );

        // 2. 添加新的标签关联
        for (String tagName : newTags) {
            Tag tag = tagMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tag>()
                            .eq(Tag::getName, tagName)
            );

            if (tag == null) {
                tag = new Tag();
                tag.setName(tagName);
                tag.setDescription("Worker动态更新时创建");
                tag.setCreateTime(LocalDateTime.now());
                tagMapper.insert(tag);
                log.info("创建新标签，名称: {}, ID: {}", tag.getName(), tag.getId());
            }

            WorkerTag workerTag = new WorkerTag();
            workerTag.setWorkerId(workerId);
            workerTag.setTagId(tag.getId());
            workerTag.setCreateTime(LocalDateTime.now());
            workerTagMapper.insert(workerTag);
        }

        log.info("Worker标签更新成功 | WorkerID: {}, 标签: {}", workerId, newTags);
        return Result.success();
    }

    /**
     * 验证AKSK有效性 (带缓存)
     */
    @PostMapping("/validate-aksk")
    @Operation(summary = "验证AKSK有效性", description = "Worker执行器验证本地存储的AKSK是否有效")
    public Result<Map<String, Boolean>> validateAKSK(@RequestParam String accessKey) {
        log.info("收到AKSK验证请求，AccessKey: {}", accessKey);
        
        AKSK aksk = akskService.getByAccessKey(accessKey);
        Map<String, Boolean> resultMap = new HashMap<>();

        if (aksk != null && "active".equals(aksk.getStatus())) {
            resultMap.put("valid", true);
            log.info("AKSK验证通过，AccessKey: {}", accessKey);
            return Result.success(resultMap);
        } else {
            resultMap.put("valid", false);
            log.warn("AKSK验证失败，AccessKey: {}", accessKey);
            return Result.error(401, "AKSK is invalid or inactive");
        }
    }

    /**
     * Worker任务结果报告接口
     */
    @PostMapping("/task-result/{workerId}")
    @Operation(summary = "Worker任务结果报告", description = "Worker执行器报告任务执行结果，更新task_run表的数据")
    public Result<Void> reportTaskResult(@PathVariable String workerId, @RequestBody TaskResultMessage resultMessage, HttpServletRequest request) {
        log.info("收到Worker任务结果报告，WorkerID: {}, TaskID: {}, TaskRunID: {}", 
                workerId, resultMessage.getTaskId(), resultMessage.getTaskRunId());
        if (!isOwnWorker(request, workerId) || (resultMessage.getWorkerId() != null && !java.util.Objects.equals(authenticatedWorkerId(request), resultMessage.getWorkerId()))) {
            return workerMismatch();
        }
        
        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", workerId);
            return Result.error(404, "Worker not found");
        }

        TaskRunStatus resultStatus = TaskRunStatus.of(resultMessage.getStatus());
        if (!taskRunFinalizer.isTerminal(resultStatus)) {
            return Result.error(400, "task result must be a terminal status");
        }

        TaskRun existingTaskRun = taskRunMapper.selectById(resultMessage.getTaskRunId());
        TaskRun taskRun = TaskRun.builder()
                .id(resultMessage.getTaskRunId())
                .taskId(resultMessage.getTaskId())
                .workerId(workerId)
                .status(resultStatus)
                .exitCode(resultMessage.getExitCode())
                .startTime(resultMessage.getStartTime())
                .endTime(resultMessage.getEndTime())
                .durationMs(resultMessage.getDurationMs())
                .errorMessage(resultMessage.getErrorMessage())
                .retryNum(existingTaskRun == null ? 0 : existingTaskRun.getRetryNum())
                .lastHeartbeatTime(LocalDateTime.now())
                .build();

        if (existingTaskRun == null) {
            taskRunMapper.insert(taskRun);
            log.info("任务执行结果插入成功，TaskRunID: {}", resultMessage.getTaskRunId());
        } else if (!taskRunFinalizer.tryAdvanceToTerminal(taskRun)) {
            log.info("迟到或重复结果已丢弃，TaskRunID: {}", resultMessage.getTaskRunId());
            return Result.error(409, "task run already terminal");
        } else {
            log.info("任务执行结果更新成功，TaskRunID: {}", resultMessage.getTaskRunId());
        }

        // 终态 -> 触发分片结算检查（非分片任务为 N=1 特例，同路径）
        shardSettlementService.onRunTerminal(resultMessage.getTaskRunId());

        if (resultMessage.getLogPath() != null && !resultMessage.getLogPath().isEmpty()) {
            List<TaskLog> taskLogs = taskLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                            .eq(TaskLog::getRunId, resultMessage.getTaskRunId())
                            .orderByDesc(TaskLog::getCreateTime)
            );
            
            TaskLog existingTaskLog = taskLogs.isEmpty() ? null : taskLogs.get(0);

            if (existingTaskLog != null) {
                existingTaskLog.setStdoutPath(resultMessage.getLogPath());
                existingTaskLog.setStderrPath(resultMessage.getLogPath());
                taskLogMapper.updateById(existingTaskLog);
                log.info("任务日志路径更新成功，TaskRunID: {}, LogPath: {}", resultMessage.getTaskRunId(), resultMessage.getLogPath());
            } else {
                TaskLog taskLog = TaskLog.builder()
                        .runId(resultMessage.getTaskRunId())
                        .stdoutPath(resultMessage.getLogPath())
                        .stderrPath(resultMessage.getLogPath())
                        .createTime(LocalDateTime.now())
                        .build();
                
                taskLogMapper.insert(taskLog);
                log.info("任务日志路径插入成功，TaskRunID: {}, LogPath: {}", resultMessage.getTaskRunId(), resultMessage.getLogPath());
            }
        }

        return Result.success();
    }

    /**
     * 发布任务接口（Task级别）
     */
    @PostMapping("/task/publish/{taskId}")
    @Operation(summary = "发布任务", description = "前端调用，发布任务到指定Worker或公共任务池")
    public Result<Void> publishTask(@PathVariable String taskId) {
        log.info("收到前端任务发布请求，TaskID: {}", taskId);
        
        try {
            taskService.publishTask(taskId);
            log.info("任务发布成功，TaskID: {}", taskId);
            return Result.success();
        } catch (IllegalArgumentException e) {
            log.warn("任务发布失败，TaskID: {}, 原因: {}", taskId, e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("任务发布失败，TaskID: {}, 异常: {}", taskId, e.getMessage(), e);
            return Result.error(500, "任务发布失败: " + e.getMessage());
        }
    }

    /**
     * Worker任务状态持续报告接口
     */
    @PostMapping("/task-status/{taskRunId}")
    @Operation(summary = "Worker任务状态持续报告", description = "Worker执行器持续报告正在运行的任务状态")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务状态报告成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或任务不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public Result<Void> reportTaskStatus(@PathVariable String taskRunId, @RequestBody TaskResultMessage taskStatus, HttpServletRequest request) {
        log.info("收到任务状态持续报告，TaskRunID: {}, WorkerID: {} Status:{}", taskRunId, taskStatus.getWorkerId(), taskStatus.getStatus());
        
        try {
            TaskRun taskRun = taskRunMapper.selectById(taskRunId);
            String authenticatedId = authenticatedWorkerId(request);
            if (taskStatus.getWorkerId() != null && !java.util.Objects.equals(authenticatedId, taskStatus.getWorkerId())) {
                return workerMismatch();
            }
            if (taskRun == null) {
                log.warn("任务执行记录不存在，TaskRunID: {}", taskRunId);
                return Result.error(400, "TaskRun not found");
            }
            if (taskRun.getWorkerId() != null && !java.util.Objects.equals(authenticatedId, taskRun.getWorkerId())) {
                return workerMismatch();
            }

            TaskRunStatus nextStatus = TaskRunStatus.of(taskStatus.getStatus());
            LocalDateTime now = LocalDateTime.now();
            if (taskRunFinalizer.isTerminal(nextStatus)) {
                TaskRun terminal = TaskRun.builder()
                        .id(taskRunId)
                        .taskId(taskRun.getTaskId())
                        .workerId(authenticatedId)
                        .status(nextStatus)
                        .endTime(now)
                        .durationMs(taskStatus.getDurationMs())
                        .errorMessage(taskStatus.getErrorMessage())
                        .retryNum(taskRun.getRetryNum())
                        .lastHeartbeatTime(now)
                        .build();
                if (!taskRunFinalizer.tryAdvanceToTerminal(terminal)) {
                    log.info("迟到或重复状态已丢弃，TaskRunID: {}", taskRunId);
                    return Result.error(409, "task run already terminal");
                }
                shardSettlementService.onRunTerminal(taskRunId);
            } else if (nextStatus == TaskRunStatus.running) {
                int updated = taskRunMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TaskRun>()
                        .eq(TaskRun::getId, taskRunId)
                        .in(TaskRun::getStatus, TaskRunStatus.pending, TaskRunStatus.running)
                        .set(TaskRun::getDurationMs, taskStatus.getDurationMs())
                        .set(TaskRun::getLastHeartbeatTime, now)
                        .set(taskRun.getWorkerId() == null, TaskRun::getWorkerId, authenticatedId));
                if (updated == 0) {
                    return Result.error(409, "task run already terminal");
                }
            } else {
                return Result.error(400, "unsupported task status");
            }

            if (taskStatus.getLogPath() != null && !taskStatus.getLogPath().isEmpty()) {
                List<TaskLog> taskLogs = taskLogMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                                .eq(TaskLog::getRunId, taskRunId)
                                .orderByDesc(TaskLog::getCreateTime)
                );
                
                TaskLog existingTaskLog = taskLogs.isEmpty() ? null : taskLogs.get(0);
                
                if (existingTaskLog == null) {
                    TaskLog newTaskLog = TaskLog.builder()
                            .runId(taskRunId)
                            .stdoutPath(taskStatus.getLogPath())
                            .stderrPath(taskStatus.getLogPath())
                            .shardBatchId(taskStatus.getShardBatchId())
                            .shardIndex(taskStatus.getShardIndex())
                            .shardTotal(taskStatus.getShardTotal())
                            .createTime(LocalDateTime.now())
                            .build();
                    taskLogMapper.insert(newTaskLog);
                    log.info("创建任务日志记录，TaskRunID: {}, LogPath: {}", taskRunId, taskStatus.getLogPath());
                } else {
                    existingTaskLog.setStdoutPath(taskStatus.getLogPath());
                    existingTaskLog.setStderrPath(taskStatus.getLogPath());
                    existingTaskLog.setShardBatchId(taskStatus.getShardBatchId());
                    existingTaskLog.setShardIndex(taskStatus.getShardIndex());
                    existingTaskLog.setShardTotal(taskStatus.getShardTotal());
                    taskLogMapper.updateById(existingTaskLog);
                    log.debug("更新任务日志路径，TaskRunID: {}, LogPath: {}", taskRunId, taskStatus.getLogPath());
                }
            }
            
            return Result.success();
        } catch (IllegalArgumentException e) {
            log.error("无效的任务状态，TaskRunID: {}, Status: {}", taskRunId, taskStatus.getStatus(), e);
            return Result.error(400, "Invalid task status: " + taskStatus.getStatus());
        } catch (Exception e) {
            log.error("任务状态报告处理失败，TaskRunID: {}", taskRunId, e);
            return Result.error(500, "Failed to process task status report: " + e.getMessage());
        }
    }
}




