package com.sjdt.scheduler.controller;

import cn.hutool.core.util.IdUtil;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.message.WorkerRegisterDTO;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.AKSK;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.domain.po.WorkerTag;
import com.sjdt.scheduler.domain.vo.WorkerRegisterVO;
import com.sjdt.scheduler.exception.InvalidEnumValueException;
import com.sjdt.scheduler.mapper.AKSKMapper;
import com.sjdt.scheduler.mapper.TagMapper;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.mapper.WorkerMapper;
import com.sjdt.scheduler.mapper.WorkerTagMapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskResultMessage;
import com.sjdt.scheduler.service.ITaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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

    public WorkerController(WorkerMapper workerMapper, AKSKMapper akskMapper, WorkerTagMapper workerTagMapper, 
                           TagMapper tagMapper, TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper, 
                           ITaskService taskService) {
        this.workerMapper = workerMapper;
        this.akskMapper = akskMapper;
        this.workerTagMapper = workerTagMapper;
        this.tagMapper = tagMapper;
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskService = taskService;
    }

    /**
     * Worker注册接口
     * 用于Worker首次启动时注册到调度器，获取AKSK
     */
    @PostMapping("/register")
    @Operation(summary = "Worker注册", description = "Worker执行器首次启动时注册到调度器，获取AKSK认证信息")
    public Result<WorkerRegisterVO> register(@RequestBody WorkerRegisterDTO registerDTO) {
        log.info("收到Worker注册请求，主机名: {}, IP: {}", registerDTO.getHost(), registerDTO.getIp());
        
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

            existingWorker.setStatus(WorkerStatus.online);
            existingWorker.setLastHeartbeat(LocalDateTime.now());
            workerMapper.updateById(existingWorker);

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
            for (Tag tagDto : registerDTO.getTags()) {
                Tag tag = tagMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tag>()
                                .eq(Tag::getName, tagDto.getName())
                                .eq(Tag::getOperator, "==")
                                .eq(Tag::getVersion, tagDto.getVersion())
                );
                
                if (tag == null) {
                    tag = new Tag();
                    tag.setName(tagDto.getName());
                    tag.setOperator("==");
                    tag.setVersion(tagDto.getVersion());
                    tag.setCreateTime(LocalDateTime.now());
                    tagMapper.insert(tag);
                    log.info("创建新标签，名称: {}, 操作符: {}, 版本: {}, ID: {}", tag.getName(), tag.getOperator(), tag.getVersion(), tag.getId());
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
    public Result<Void> heartbeat(@PathVariable String workerId) {
        log.info("收到Worker心跳请求，WorkerID: {}", workerId);
        
        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", workerId);
            return Result.error(404, "Worker not found");
        }

        worker.setStatus(WorkerStatus.online);
        worker.setLastHeartbeat(LocalDateTime.now());
        workerMapper.updateById(worker);
        log.info("Worker心跳更新成功，WorkerID: {}", workerId);

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
                                     @RequestParam Integer runningTasks) {
        log.info("收到Worker状态报告，WorkerID: {}", workerId);
        
        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", workerId);
            return Result.error(404, "Worker not found");
        }

        worker.setCpuUsage(cpuUsage);
        worker.setMemUsage(memUsage);
        worker.setRunningTasks(runningTasks);
        worker.setStatus(WorkerStatus.online);
        worker.setLastHeartbeat(LocalDateTime.now());
        worker.setUpdateTime(LocalDateTime.now());
        workerMapper.updateById(worker);
        log.info("Worker状态更新成功，WorkerID: {}", workerId);

        return Result.success();
    }

    /**
     * 验证AKSK有效性
     */
    @PostMapping("/validate-aksk")
    @Operation(summary = "验证AKSK有效性", description = "Worker执行器验证本地存储的AKSK是否有效")
    public Result<Map<String, Boolean>> validateAKSK(@RequestParam String accessKey) {
        log.info("收到AKSK验证请求，AccessKey: {}", accessKey);
        
        AKSK aksk = akskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AKSK>()
                        .eq(AKSK::getAccessKey, accessKey)
        );
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
    public Result<Void> reportTaskResult(@PathVariable String workerId, @RequestBody TaskResultMessage resultMessage) {
        log.info("收到Worker任务结果报告，WorkerID: {}, TaskID: {}, TaskRunID: {}", 
                workerId, resultMessage.getTaskId(), resultMessage.getTaskRunId());
        
        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", workerId);
            return Result.error(404, "Worker not found");
        }

        TaskRun taskRun = TaskRun.builder()
                .id(resultMessage.getTaskRunId())
                .taskId(resultMessage.getTaskId())
                .workerId(workerId)
                .status(TaskRunStatus.of(resultMessage.getStatus()))
                .exitCode(resultMessage.getExitCode())
                .startTime(resultMessage.getStartTime())
                .endTime(resultMessage.getEndTime())
                .durationMs(resultMessage.getDurationMs())
                .errorMessage(resultMessage.getErrorMessage())
                .build();

        TaskRun existingTaskRun = taskRunMapper.selectById(resultMessage.getTaskRunId());
        if (existingTaskRun != null) {
            taskRunMapper.updateById(taskRun);
            log.info("任务执行结果更新成功，TaskRunID: {}", resultMessage.getTaskRunId());
        } else {
            taskRunMapper.insert(taskRun);
            log.info("任务执行结果插入成功，TaskRunID: {}", resultMessage.getTaskRunId());
        }
        
        if (resultMessage.getLogPath() != null && !resultMessage.getLogPath().isEmpty()) {
            TaskLog taskLog = TaskLog.builder()
                    .runId(resultMessage.getTaskRunId())
                    .stdoutPath(resultMessage.getLogPath())
                    .stderrPath(resultMessage.getLogPath())
                    .createTime(LocalDateTime.now())
                    .build();
            
            taskLogMapper.insert(taskLog);
            log.info("任务日志路径保存成功，TaskRunID: {}, LogPath: {}", resultMessage.getTaskRunId(), resultMessage.getLogPath());
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
    public Result<Void> reportTaskStatus(@PathVariable String taskRunId, @RequestBody TaskResultMessage taskStatus) {
        log.info("收到任务状态持续报告，TaskRunID: {}, WorkerID: {} Status:{}", taskRunId, taskStatus.getWorkerId(), taskStatus.getStatus());
        
        try {
            TaskRun taskRun = taskRunMapper.selectById(taskRunId);
            if (taskRun == null) {
                log.warn("任务执行记录不存在，TaskRunID: {}", taskRunId);
                return Result.error(400, "TaskRun not found");
            }

            taskRun.setStatus(TaskRunStatus.valueOf(taskStatus.getStatus()));

            if ("running".equals(taskStatus.getStatus())) {
                taskRun.setDurationMs(taskStatus.getDurationMs());
                
                if (taskRun.getWorkerId() == null || taskRun.getWorkerId().isEmpty()) {
                    taskRun.setWorkerId(taskStatus.getWorkerId());
                    log.info("更新任务Worker ID，TaskRunID: {}, WorkerID: {}", taskRunId, taskStatus.getWorkerId());
                }
            }

            taskRunMapper.updateById(taskRun);
            log.debug("任务状态更新成功，TaskRunID: {}, 状态: {}, 持续时间: {}ms, WorkerID: {}",
                    taskRunId, taskStatus.getStatus(), taskStatus.getDurationMs(), taskStatus.getWorkerId());
            
            if (taskStatus.getLogPath() != null && !taskStatus.getLogPath().isEmpty()) {
                TaskLog existingTaskLog = taskLogMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                                .eq(TaskLog::getRunId, taskRunId)
                );
                
                if (existingTaskLog == null) {
                    TaskLog newTaskLog = TaskLog.builder()
                            .runId(taskRunId)
                            .stdoutPath(taskStatus.getLogPath())
                            .stderrPath(taskStatus.getLogPath())
                            .createTime(LocalDateTime.now())
                            .build();
                    taskLogMapper.insert(newTaskLog);
                    log.info("创建任务日志记录，TaskRunID: {}, LogPath: {}", taskRunId, taskStatus.getLogPath());
                } else {
                    existingTaskLog.setStdoutPath(taskStatus.getLogPath());
                    existingTaskLog.setStderrPath(taskStatus.getLogPath());
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
