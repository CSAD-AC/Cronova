package com.sjdt.scheduler.controller;

import cn.hutool.core.util.IdUtil;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.message.WorkerRegisterDTO;
import com.sjdt.scheduler.domain.message.WorkerStatusDTO;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.AKSK;
import com.sjdt.scheduler.domain.po.Tag;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.domain.po.WorkerTag;
import com.sjdt.scheduler.domain.vo.WorkerRegisterVO;
import com.sjdt.scheduler.mapper.AKSKMapper;
import com.sjdt.scheduler.mapper.TagMapper;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.mapper.WorkerMapper;
import com.sjdt.scheduler.mapper.WorkerTagMapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskResultMessage;
import com.sjdt.scheduler.service.IWorkerTagService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.domain.po.Task;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Worker 控制器
 * 处理 Worker 执行器的注册、状态报告、心跳等请求
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

    public WorkerController(WorkerMapper workerMapper, AKSKMapper akskMapper, WorkerTagMapper workerTagMapper, TagMapper tagMapper, TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper, ITaskService taskService) {
        this.workerMapper = workerMapper;
        this.akskMapper = akskMapper;
        this.workerTagMapper = workerTagMapper;
        this.tagMapper = tagMapper;
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskService = taskService;
    }

    /**
     * Worker 注册接口
     * 用于 Worker 首次启动时注册到调度器，获取 AKSK
     * @param registerDTO Worker 注册信息
     * @return 注册结果，包含 AKSK 信息
     */
    @PostMapping("/register")
    @Operation(summary = "Worker注册", description = "Worker执行器首次启动时注册到调度器，获取AKSK认证信息")
    public Result<WorkerRegisterVO> register(@RequestBody WorkerRegisterDTO registerDTO) {
        log.info("收到Worker注册请求，主机名: {}, IP: {}", registerDTO.getHost(), registerDTO.getIp());
        
        // 检查 Worker 是否已存在（根据主机名或 IP）
        Worker existingWorker = workerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Worker>()
                        .eq(Worker::getHost, registerDTO.getHost())
                        .or()
                        .eq(Worker::getIp, registerDTO.getIp())
        );

        WorkerRegisterVO registerVO = new WorkerRegisterVO();

        if (existingWorker != null) {
            log.info("Worker已存在，ID: {}", existingWorker.getId());
            // Worker 已存在，检查是否已有 AKSK
            AKSK aksk = akskMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AKSK>()
                            .eq(AKSK::getWorkerId, existingWorker.getId())
                            .eq(AKSK::getStatus, "active")
            );

            // 更新 Worker 状态
            existingWorker.setStatus(WorkerStatus.online);
            existingWorker.setLastHeartbeat(LocalDateTime.now());
            workerMapper.updateById(existingWorker);

            // 设置返回结果
            registerVO.setWorkerId(existingWorker.getId());
            registerVO.setAccessKey(aksk.getAccessKey());
            registerVO.setSecretKey(aksk.getSecretKey());

            log.info("Worker注册成功，已存在Worker，返回现有Worker信息和AKSK");
            return Result.success(registerVO);
        }

        log.info("Worker不存在，创建新的Worker记录");
        // Worker 不存在，创建新的 Worker
        Worker newWorker = new Worker();
        newWorker.setHost(registerDTO.getHost());
        newWorker.setIp(registerDTO.getIp());
        newWorker.setStatus(WorkerStatus.online);
        newWorker.setLastHeartbeat(LocalDateTime.now());
        newWorker.setCreateTime(LocalDateTime.now());
        newWorker.setUpdateTime(LocalDateTime.now());
        workerMapper.insert(newWorker);

        // 生成 AKSK
        // 复制 AKSKServiceImpl.generateAKSK 的逻辑
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

        // 处理标签
        if (registerDTO.getTags() != null && !registerDTO.getTags().isEmpty()) {
            log.info("为新Worker处理标签，WorkerID: {}, 标签数量: {}", newWorker.getId(), registerDTO.getTags().size());
            for (Tag tagDto : registerDTO.getTags()) {
                // 根据name、operator和version查询标签，不存在则创建
                Tag tag = tagMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tag>()
                                .eq(Tag::getName, tagDto.getName())
                                .eq(Tag::getOperator, "==")
                                .eq(Tag::getVersion, tagDto.getVersion())
                );
                
                if (tag == null) {
                    // 创建新标签
                    tag = new Tag();
                    tag.setName(tagDto.getName());
                    tag.setOperator("==");
                    tag.setVersion(tagDto.getVersion());
                    tag.setCreateTime(LocalDateTime.now());
                    tagMapper.insert(tag);
                    log.info("创建了新标签，名称: {}, 操作符: {}, 版本: {}, ID: {}", tag.getName(), tag.getOperator(), tag.getVersion(), tag.getId());
                }
                
                // 关联标签到Worker
                WorkerTag workerTag = new WorkerTag();
                workerTag.setWorkerId(newWorker.getId());
                workerTag.setTagId(tag.getId());
                workerTag.setCreateTime(LocalDateTime.now());
                workerTagMapper.insert(workerTag);
            }
        }

        // 设置返回结果
        registerVO.setWorkerId(newWorker.getId());
        registerVO.setAccessKey(aksk.getAccessKey());
        registerVO.setSecretKey(aksk.getSecretKey());

        log.info("Worker注册成功，创建了新的Worker记录，ID: {}", newWorker.getId());
        return Result.success(registerVO);
    }

    /**
     * Worker 心跳接口
     * 用于 Worker 定期发送心跳，保持在线状态
     * @param workerId Worker ID
     * @return 心跳结果
     */
    @PostMapping("/heartbeat/{workerId}")
    @Operation(summary = "Worker心跳", description = "Worker执行器定期发送心跳，保持在线状态")
    public Result<Void> heartbeat(@PathVariable String workerId) {
        log.info("收到Worker心跳请求，WorkerID: {}", workerId);
        
        // 更新 Worker 最后心跳时间和状态
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
     * Worker 状态报告接口
     * 用于 Worker 定期向调度器报告状态
     * @param workerId Worker ID
     * @param statusDTO Worker 状态信息
     * @return 状态报告结果
     */
    @PostMapping("/status/{workerId}")
    @Operation(summary = "Worker状态报告", description = "Worker执行器定期向调度器报告CPU、内存使用率和运行任务数")
    public Result<Void> reportStatus(@PathVariable String workerId, @RequestBody WorkerStatusDTO statusDTO) {
        log.info("收到Worker状态报告，WorkerID: {}", workerId);
        
        // 更新 Worker 状态
        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", workerId);
            return Result.error(404, "Worker not found");
        }

        // 更新 CPU 使用率
        worker.setCpuUsage(statusDTO.getCpuUsage());

        // 更新内存使用率
        worker.setMemUsage(statusDTO.getMemUsage());

        // 更新运行任务数
        worker.setRunningTasks(statusDTO.getRunningTasks());

        worker.setStatus(WorkerStatus.online);
        worker.setLastHeartbeat(LocalDateTime.now());
        worker.setUpdateTime(LocalDateTime.now());
        workerMapper.updateById(worker);
        log.info("Worker状态更新成功，WorkerID: {}", workerId);

        return Result.success();
    }

    /**
     * 验证 AKSK 有效性
     * 用于 Worker 验证本地存储的 AKSK 是否有效
     * @param accessKey Access Key
     * @return 验证结果
     */
    @PostMapping("/validate-aksk")
    @Operation(summary = "验证AKSK有效性", description = "Worker执行器验证本地存储的AKSK是否有效")
    public Result<Map<String, Boolean>> validateAKSK(@RequestParam String accessKey) {
        log.info("收到AKSK验证请求，AccessKey: {}", accessKey);
        
        // 检查 AKSK 是否有效
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
     * Worker 任务结果报告接口
     * 用于 Worker 定期报告任务执行结果，更新任务状态
     * @param workerId Worker ID
     * @param resultMessage 任务执行结果消息
     * @return 状态报告结果
     */
    @PostMapping("/task-result/{workerId}")
    @Operation(summary = "Worker任务结果报告", description = "Worker执行器定期报告任务执行结果，更新task_run表的数据")
    public Result<Void> reportTaskResult(@PathVariable String workerId, @RequestBody TaskResultMessage resultMessage) {
        log.info("收到Worker任务结果报告，WorkerID: {}, TaskID: {}, TaskRunID: {}", 
                workerId, resultMessage.getTaskId(), resultMessage.getTaskRunId());
        
        // 检查 Worker 是否存在
        Worker worker = workerMapper.selectById(workerId);
        if (worker == null) {
            log.warn("Worker不存在，WorkerID: {}", workerId);
            return Result.error(404, "Worker not found");
        }

        // 转换任务结果消息为TaskRun对象
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

        // 更新或插入task_run表
        TaskRun existingTaskRun = taskRunMapper.selectById(resultMessage.getTaskRunId());
        if (existingTaskRun != null) {
            // 更新现有记录
            taskRunMapper.updateById(taskRun);
            log.info("任务执行结果更新成功，TaskRunID: {}", resultMessage.getTaskRunId());
        } else {
            // 插入新记录
            taskRunMapper.insert(taskRun);
            log.info("任务执行结果插入成功，TaskRunID: {}", resultMessage.getTaskRunId());
        }
        
        // 保存日志路径到task_log表
        if (resultMessage.getLogPath() != null && !resultMessage.getLogPath().isEmpty()) {
            TaskLog taskLog = TaskLog.builder()
                    .runId(resultMessage.getTaskRunId())
                    .stdoutPath(resultMessage.getLogPath())
                    .stderrPath(resultMessage.getLogPath()) // 目前只支持单个日志路径，stdout和stderr使用相同路径
                    .createTime(LocalDateTime.now())
                    .build();
            
            taskLogMapper.insert(taskLog);
            log.info("任务日志路径保存成功，TaskRunID: {}, LogPath: {}", resultMessage.getTaskRunId(), resultMessage.getLogPath());
        }

        return Result.success();
    }

    /**
     * 发布任务接口
     * 用于前端调用，发布任务到指定Worker或公共任务池
     * @param taskId 任务ID
     * @return 发布结果
     */
    @PostMapping("/task/publish/{taskId}")
    @Operation(summary = "发布任务", description = "前端调用，发布任务到指定Worker或公共任务池")
    public Result<Void> publishTask(@PathVariable String taskId) {
        log.info("收到前端任务发布请求，TaskID: {}", taskId);
        
        try {
            // 调用TaskService发布任务
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

}