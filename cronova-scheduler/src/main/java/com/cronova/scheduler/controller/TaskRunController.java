package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.TaskPauseMessage;
import com.cronova.scheduler.domain.message.TaskResumeMessage;
import com.cronova.scheduler.domain.message.TaskStopMessage;
import com.cronova.scheduler.domain.po.TaskLog;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.domain.vo.LogFileInfo;
import com.cronova.scheduler.domain.vo.LogOffsetResult;
import com.cronova.scheduler.mapper.TaskLogMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.service.TaskControlProducerService;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 任务运行实例控制器
 * 处理前端对任务运行实例的控制操作（暂停、恢复、停止、删除、查询、日志下载）
 */
@RestController
@RequestMapping("/api/task-run")
@io.swagger.v3.oas.annotations.tags.Tag(name = "任务运行实例API", description = "任务运行实例的查询、控制和日志管理")
@Slf4j
public class TaskRunController {

    private final TaskRunMapper taskRunMapper;
    private final TaskLogMapper taskLogMapper;
    private final TaskControlProducerService taskControlProducerService;
    private final MinioClient minioClient;

    @org.springframework.beans.factory.annotation.Value("${minio.bucket-name:task-engine}")
    private String defaultBucketName;

    @Autowired
    public TaskRunController(TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper, 
                            TaskControlProducerService taskControlProducerService,
                            MinioClient minioClient) {
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskControlProducerService = taskControlProducerService;
        this.minioClient = minioClient;
    }

    /**
     * 获取所有任务运行实例列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取任务运行实例列表", description = "获取所有任务运行实例的列表，按创建时间倒序排列")
    public Result<List<TaskRun>> listTaskRuns() {
        log.info("收到获取所有任务运行实例列表请求");
        List<TaskRun> taskRuns = taskRunMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRun>()
                        .orderByDesc(TaskRun::getCreateTime)
        );
        return Result.success(taskRuns);
    }

    /**
     * 根据任务ID查询任务运行实例列表
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "根据任务ID查询实例列表", description = "获取指定任务的所有运行实例列表")
    public Result<List<TaskRun>> getTaskRunsByTaskId(@PathVariable String taskId) {
        log.info("收到根据任务ID查询实例列表请求，TaskID: {}", taskId);
        List<TaskRun> taskRuns = taskRunMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRun>()
                        .eq(TaskRun::getTaskId, taskId)
                        .orderByDesc(TaskRun::getCreateTime)
        );
        return Result.success(taskRuns);
    }

    /**
     * 查询任务运行实例详情
     */
    @GetMapping("/{taskRunId}")
    @Operation(summary = "查询任务运行实例详情", description = "获取任务运行实例的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<TaskRun> getTaskRun(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId) {
        log.info("收到查询任务运行实例详情请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        return Result.success(taskRun);
    }

    /**
     * 查询任务运行实例日志
     */
    @GetMapping("/{taskRunId}/logs")
    @Operation(summary = "查询任务运行实例日志", description = "获取任务运行实例的日志信息（MinIO路径）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "404", description = "任务运行实例或日志不存在")
    })
    public Result<TaskLog> getTaskRunLogs(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId) {
        log.info("收到查询任务运行实例日志请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        TaskLog taskLog = taskLogMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getRunId, taskRunId)
        );

        if (taskLog == null) {
            log.warn("任务日志不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskLog not found");
        }

        return Result.success(taskLog);
    }

    /**
     * 获取任务运行实例日志文件信息
     */
    @GetMapping("/{taskRunId}/log/info")
    @Operation(summary = "获取任务运行实例日志文件信息", description = "获取任务运行实例日志文件的MinIO访问信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "400", description = "日志路径格式错误"),
        @ApiResponse(responseCode = "404", description = "任务运行实例或日志不存在")
    })
    public Result<LogFileInfo> getLogFileInfo(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId) {
        log.info("收到获取日志文件信息请求，TaskRunID: {}", taskRunId);
        
        try {
            TaskRun taskRun = taskRunMapper.selectById(taskRunId);
            if (taskRun == null) {
                log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
                return Result.error(404, "TaskRun not found");
            }

            List<TaskLog> taskLogs = taskLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                            .eq(TaskLog::getRunId, taskRunId)
                            .orderByDesc(TaskLog::getCreateTime)
            );
            TaskLog taskLog = taskLogs.isEmpty() ? null : taskLogs.get(0);
            if (taskLog == null) {
                log.warn("日志记录不存在，TaskRunID: {}", taskRunId);
                return Result.error(404, "TaskLog not found");
            }
            
            String logPath = taskLog.getStdoutPath();
            log.info("日志文件路径：{}", logPath);
            
            // 统一解析逻辑：优先识别桶名，否则使用默认桶
            String bucketName = defaultBucketName != null ? defaultBucketName : "task-engine";
            String objectName = logPath;
            
            if (logPath != null && logPath.contains("/")) {
                int firstSlashIndex = logPath.indexOf('/');
                String firstPart = logPath.substring(0, firstSlashIndex);
                String remainingPart = logPath.substring(firstSlashIndex + 1);
                
                // 如果第一部分匹配默认桶名，则将其分离
                if (firstPart.equals(bucketName)) {
                    objectName = remainingPart;
                }
                // 否则，保持 bucketName 为默认桶，objectName 为完整 logPath
            }
            
            String fileName = "task_log_" + taskRunId + ".log";
            log.info("解析日志路径成功，桶名：{}，对象名：{}", bucketName, objectName);
            
            LogFileInfo logFileInfo = new LogFileInfo();
            logFileInfo.setBucket(bucketName);
            logFileInfo.setObjectName(objectName);
            logFileInfo.setFileName(fileName);
            
            // 生成预签名URL，让前端可以直接访问
            try {
                String signedUrl = minioClient.getPresignedObjectUrl(
                    io.minio.GetPresignedObjectUrlArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .method(io.minio.http.Method.GET)
                        .expiry(60 * 60) // 1小时过期
                        .build()
                );
                logFileInfo.setSignedUrl(signedUrl);
                log.info("生成预签名URL成功: {}", signedUrl);
            } catch (Exception e) {
                log.error("生成预签名URL失败: {}", taskRunId, e);
                // 不影响主要业务流程，预签名URL失败时只记录日志
            }
            
            return Result.success(logFileInfo);
        } catch (Exception e) {
            log.error("获取日志文件信息失败，TaskRunID: {}", taskRunId, e);
            return Result.error(500, "Failed to get log file info");
        }
    }

    /**
     * 增量获取任务运行日志（offset 协议）
     * 按字节偏移读取 [offset, offset+limit) 区间，返回 nextOffset 供前端轮询。
     */
    @GetMapping("/{taskRunId}/log")
    @Operation(summary = "增量获取任务运行日志", description = "按字节偏移增量读取日志，前端轮询")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<LogOffsetResult> getTaskRunLog(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId,
            @Parameter(description = "字节偏移（首次为0）") @RequestParam(defaultValue = "0") long offset,
            @Parameter(description = "读取长度上限（字节），默认64KB，最大1MB") @RequestParam(defaultValue = "65536") int limit) {
        log.info("收到增量获取任务运行日志请求，TaskRunID: {}, offset: {}, limit: {}", taskRunId, offset, limit);
        try {
            TaskRun taskRun = taskRunMapper.selectById(taskRunId);
            if (taskRun == null) {
                log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
                return Result.error(404, "TaskRun not found");
            }

            List<TaskLog> taskLogs = taskLogMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                            .eq(TaskLog::getRunId, taskRunId)
                            .orderByDesc(TaskLog::getCreateTime)
            );
            TaskLog taskLog = taskLogs.isEmpty() ? null : taskLogs.get(0);

            // 日志记录尚未落库（任务刚提交）：返回空内容与未终止标记，前端继续轮询
            if (taskLog == null || taskLog.getStdoutPath() == null || taskLog.getStdoutPath().isBlank()) {
                return successOffsetResult(offset, 0, 0, 0, isTerminal(taskRun.getStatus()));
            }

            String bucket = defaultBucketName != null ? defaultBucketName : "task-engine";
            String object = taskLog.getStdoutPath();
            if (object.contains("/")) {
                int firstSlashIndex = object.indexOf('/');
                String firstPart = object.substring(0, firstSlashIndex);
                if (firstPart.equals(bucket)) {
                    object = object.substring(firstSlashIndex + 1);
                }
            }

            boolean finalized = isTerminal(taskRun.getStatus());

            // 对象当前大小（日志对象尚未创建时按"空日志"处理，前端继续轮询）
            long size;
            try {
                StatObjectResponse stat = minioClient.statObject(
                        StatObjectArgs.builder().bucket(bucket).object(object).build());
                size = stat.size();
            } catch (Exception e) {
                log.debug("日志对象尚未创建，TaskRunID: {}, object: {}", taskRunId, object);
                return successOffsetResult(offset, 0, Math.max(0, offset), 0, finalized);
            }

            int maxLimit = Math.min(Math.max(limit, 0), 1024 * 1024);

            // 偏移失效：对象被重建/截断（如分片合并、保留窗口清理），通知前端从头重读
            if (offset > size) {
                LogOffsetResult result = new LogOffsetResult();
                result.setContent("");
                result.setOffset(offset);
                result.setNextOffset(0);
                result.setSize(size);
                result.setTruncatedHeadBytes(size);
                result.setFinalized(finalized);
                return Result.success(result);
            }

            long from = Math.max(0, offset);
            long to = Math.min(size, from + maxLimit);
            if (from >= to) {
                // 已读到当前末尾：返回空内容，偏移停在 size，等待新数据或任务终止
                return successOffsetResult(offset, size, size, 0, finalized);
            }

            int len = (int) (to - from);
            byte[] data;
            try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket).object(object).offset(from).length((long) len).build())) {
                data = in.readNBytes(len);
            }
            String content = decodeUtf8(data);
            long nextOffset = from + content.getBytes(StandardCharsets.UTF_8).length;
            LogOffsetResult result = new LogOffsetResult();
            result.setContent(content);
            result.setOffset(offset);
            result.setNextOffset(nextOffset);
            result.setSize(size);
            result.setTruncatedHeadBytes(0);
            result.setFinalized(finalized);
            return Result.success(result);
        } catch (Exception e) {
            log.error("增量获取任务运行日志失败，TaskRunID: {}", taskRunId, e);
            return Result.error(500, "Failed to get task run log");
        }
    }

    private static Result<LogOffsetResult> successOffsetResult(long offset, long nextOffset, long size,
                                                       long truncatedHeadBytes, boolean finalized) {
        LogOffsetResult result = new LogOffsetResult();
        result.setContent("");
        result.setOffset(offset);
        result.setNextOffset(nextOffset);
        result.setSize(size);
        result.setTruncatedHeadBytes(truncatedHeadBytes);
        result.setFinalized(finalized);
        return Result.success(result);
    }

    /** 任务是否已终止（终止后前端停止轮询） */
    private boolean isTerminal(TaskRunStatus status) {
        return status == TaskRunStatus.success
                || status == TaskRunStatus.failed
                || status == TaskRunStatus.timeout;
    }

    /** UTF-8 解码并丢弃末尾不完整多字节序列（避免跨 chunk 截断产生乱码） */
    private static String decodeUtf8(byte[] bytes) {
        int end = bytes.length;
        while (end > 0) {
            byte b = bytes[end - 1];
            if (b >= 0) {
                break; // ASCII 完整
            }
            // 找到末尾多字节序列的头部，判断是否完整
            int start = end - 1;
            while (start > 0 && (bytes[start] & 0xC0) == 0x80) {
                start--;
            }
            int header = bytes[start] & 0xFF;
            int expected;
            if ((header & 0xE0) == 0xC0) expected = 2;
            else if ((header & 0xF0) == 0xE0) expected = 3;
            else if ((header & 0xF8) == 0xF0) expected = 4;
            else expected = 1;
            if (end - start >= expected) {
                break; // 序列完整
            }
            end = start; // 截掉末尾不完整序列（下个 chunk 会带来其后续字节）
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /**
     * 暂停任务运行实例
     */
    @PostMapping("/{taskRunId}/pause")
    @SaCheckRole("admin")
    @Operation(summary = "暂停任务运行实例", description = "通过MQ向执行器发送暂停指令，暂停正在运行的任务实例")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "暂停指令发送成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或任务状态不允许暂停"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<Void> pauseTaskRun(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId,
            @Parameter(description = "暂停原因") @RequestParam(required = false) String reason) {
        log.info("收到暂停任务运行实例请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        if (!"running".equals(taskRun.getStatus().name())) {
            log.warn("任务运行实例不是运行状态，无法暂停，TaskRunID: {}，状态: {}", taskRunId, taskRun.getStatus());
            return Result.error(400, "TaskRun is not in running status");
        }

        if (taskRun.getWorkerId() == null || taskRun.getWorkerId().isEmpty()) {
            log.warn("任务运行实例未分配执行器，TaskRunID: {}", taskRunId);
            return Result.error(400, "TaskRun has no assigned worker");
        }

        TaskPauseMessage message = TaskPauseMessage.builder()
                .taskId(taskRun.getTaskId())
                .taskRunId(taskRunId)
                .targetWorkerId(taskRun.getWorkerId())
                .reason(reason != null ? reason : "用户手动暂停")
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        taskControlProducerService.sendPauseTask(message);
        log.info("暂停任务运行实例消息已发送，TaskRunID: {}，WorkerID: {}", taskRunId, taskRun.getWorkerId());

        return Result.success();
    }

    /**
     * 恢复任务运行实例
     */
    @PostMapping("/{taskRunId}/resume")
    @SaCheckRole("admin")
    @Operation(summary = "恢复任务运行实例", description = "通过MQ向执行器发送恢复指令，恢复已暂停的任务实例")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "恢复指令发送成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或任务状态不允许恢复"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<Void> resumeTaskRun(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId,
            @Parameter(description = "恢复原因") @RequestParam(required = false) String reason) {
        log.info("收到恢复任务运行实例请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        if (!"paused".equals(taskRun.getStatus().name())) {
            log.warn("任务运行实例不是暂停状态，无法恢复，TaskRunID: {}，状态: {}", taskRunId, taskRun.getStatus());
            return Result.error(400, "TaskRun is not in paused status");
        }

        if (taskRun.getWorkerId() == null || taskRun.getWorkerId().isEmpty()) {
            log.warn("任务运行实例未分配执行器，TaskRunID: {}", taskRunId);
            return Result.error(400, "TaskRun has no assigned worker");
        }

        TaskResumeMessage message = TaskResumeMessage.builder()
                .taskId(taskRun.getTaskId())
                .taskRunId(taskRunId)
                .targetWorkerId(taskRun.getWorkerId())
                .reason(reason != null ? reason : "用户手动恢复")
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        taskControlProducerService.sendResumeTask(message);
        log.info("恢复任务运行实例消息已发送，TaskRunID: {}，WorkerID: {}", taskRunId, taskRun.getWorkerId());

        return Result.success();
    }

    /**
     * 停止任务运行实例（SIGTERM信号）
     */
    @PostMapping("/{taskRunId}/stop")
    @SaCheckRole("admin")
    @Operation(summary = "停止任务运行实例", description = "通过MQ向执行器发送停止指令，使用SIGTERM信号正常终止任务")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "停止指令发送成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或任务状态不允许停止"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<Void> stopTaskRun(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId,
            @Parameter(description = "停止原因") @RequestParam(required = false) String reason) {
        log.info("收到停止任务运行实例请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        if (!"running".equals(taskRun.getStatus().name()) && !"paused".equals(taskRun.getStatus().name())) {
            log.warn("任务运行实例不是运行或暂停状态，无法停止，TaskRunID: {}，状态: {}", taskRunId, taskRun.getStatus());
            return Result.error(400, "TaskRun is not in running or paused status");
        }

        if (taskRun.getWorkerId() == null || taskRun.getWorkerId().isEmpty()) {
            log.warn("任务运行实例未分配执行器，TaskRunID: {}", taskRunId);
            return Result.error(400, "TaskRun has no assigned worker");
        }

        TaskStopMessage message = TaskStopMessage.builder()
                .taskId(taskRun.getTaskId())
                .taskRunId(taskRunId)
                .targetWorkerId(taskRun.getWorkerId())
                .reason(reason != null ? reason : "用户手动停止")
                .build();

        taskControlProducerService.sendStopTask(message);
        log.info("停止任务运行实例消息已发送，TaskRunID: {}，WorkerID: {}", taskRunId, taskRun.getWorkerId());

        return Result.success();
    }

    /**
     * 强制终止任务运行实例（SIGKILL信号）
     */
    @PostMapping("/{taskRunId}/kill")
    @SaCheckRole("admin")
    @Operation(summary = "强制终止任务运行实例", description = "通过MQ向执行器发送强制终止指令，使用SIGKILL信号立即终止任务")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "强制终止指令发送成功"),
        @ApiResponse(responseCode = "400", description = "参数错误或任务状态不允许强制终止"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<Void> killTaskRun(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId,
            @Parameter(description = "终止原因") @RequestParam(required = false) String reason) {
        log.info("收到强制终止任务运行实例请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        if (!"running".equals(taskRun.getStatus().name()) && !"paused".equals(taskRun.getStatus().name())) {
            log.warn("任务运行实例不是运行或暂停状态，无法强制终止，TaskRunID: {}，状态: {}", taskRunId, taskRun.getStatus());
            return Result.error(400, "TaskRun is not in running or paused status");
        }

        if (taskRun.getWorkerId() == null || taskRun.getWorkerId().isEmpty()) {
            log.warn("任务运行实例未分配执行器，TaskRunID: {}", taskRunId);
            return Result.error(400, "TaskRun has no assigned worker");
        }

        TaskStopMessage message = TaskStopMessage.builder()
                .taskId(taskRun.getTaskId())
                .taskRunId(taskRunId)
                .targetWorkerId(taskRun.getWorkerId())
                .reason(reason != null ? reason : "用户强制终止")
                .build();

        taskControlProducerService.sendKillTask(message);
        log.info("强制终止任务运行实例消息已发送，TaskRunID: {}，WorkerID: {}", taskRunId, taskRun.getWorkerId());

        return Result.success();
    }

    /**
     * 删除任务运行实例
     */
    @DeleteMapping("/{taskRunId}")
    @SaCheckRole("admin")
    @Operation(summary = "删除任务运行实例", description = "删除任务运行实例记录，如果任务正在运行则先停止")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "任务运行实例不存在")
    })
    public Result<Void> deleteTaskRun(
            @Parameter(description = "任务运行实例ID", required = true) @PathVariable String taskRunId,
            @Parameter(description = "删除原因") @RequestParam(required = false) String reason) {
        log.info("收到删除任务运行实例请求，TaskRunID: {}", taskRunId);
        
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        if (taskRun == null) {
            log.warn("任务运行实例不存在，TaskRunID: {}", taskRunId);
            return Result.error(404, "TaskRun not found");
        }

        if (taskRun.getWorkerId() != null && !taskRun.getWorkerId().isEmpty()) {
            if ("running".equals(taskRun.getStatus().name()) || "paused".equals(taskRun.getStatus().name())) {
                TaskStopMessage stopMessage = TaskStopMessage.builder()
                        .taskId(taskRun.getTaskId())
                        .taskRunId(taskRunId)
                        .targetWorkerId(taskRun.getWorkerId())
                        .reason(reason != null ? reason : "删除任务运行实例")
                        .build();
                
                if ("running".equals(taskRun.getStatus().name())) {
                    taskControlProducerService.sendStopTask(stopMessage);
                    log.info("停止任务运行实例消息已发送，TaskRunID: {}", taskRunId);
                } else {
                    taskControlProducerService.sendKillTask(stopMessage);
                    log.info("强制终止任务运行实例消息已发送，TaskRunID: {}", taskRunId);
                }
            }
        }

        taskRunMapper.deleteById(taskRunId);
        taskLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                .eq(TaskLog::getRunId, taskRunId));
        
        log.info("任务运行实例已删除，TaskRunID: {}", taskRunId);

        return Result.success();
    }
}
