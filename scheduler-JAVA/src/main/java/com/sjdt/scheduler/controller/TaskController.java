package com.sjdt.scheduler.controller;

import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.dto.TaskCreateDTO;
import com.sjdt.scheduler.domain.dto.TaskUpdateDTO;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.domain.vo.TaskVO;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.ITaskService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 任务管理Controller
 * 处理任务的CRUD操作和发布任务
 */
@RestController
@RequestMapping("/api/tasks")
@io.swagger.v3.oas.annotations.tags.Tag(name = "任务管理", description = "任务的创建、查询、更新、删除和发布")
public class TaskController {

    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    
    private final ITaskService taskService;
    private final TaskRunMapper taskRunMapper;
    private final TaskLogMapper taskLogMapper;
    private final MinioClient minioClient;
    
    @Autowired
    public TaskController(ITaskService taskService, TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper, MinioClient minioClient) {
        this.taskService = taskService;
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.minioClient = minioClient;
    }

    /**
     * 创建任务
     *
     * @param taskCreateDTO 任务创建请求DTO
     * @return 创建成功的任务信息
     */
    @PostMapping
    @Operation(summary = "创建任务", description = "创建一个新的任务，可以是一次性任务或定时任务")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务创建成功", 
                     content = @Content(schema = @Schema(implementation = TaskVO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<TaskVO> createTask(@Valid @RequestBody TaskCreateDTO taskCreateDTO) {
        logger.info("开始创建任务，任务名称：{}", taskCreateDTO.getName());
        try {
            // 构建任务对象
            Task task = Task.builder()
                    .name(taskCreateDTO.getName())
                    .description(taskCreateDTO.getDescription())
                    .type(taskCreateDTO.getType())
                    .scriptId(taskCreateDTO.getScriptId())
                    .taskType(taskCreateDTO.getTaskType())
                    .cronExpr(taskCreateDTO.getCronExpr())
                    .enabled(true) // 默认为启用状态
                    .timeoutSeconds(taskCreateDTO.getTimeoutSeconds() != null ? taskCreateDTO.getTimeoutSeconds() : 0)
                    .targetWorker(taskCreateDTO.getTargetWorker())
                    .status(TaskStatus.active) // 默认为活跃状态
                    .createdBy("system") // 这里可以从当前登录用户获取
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            // 保存任务
            taskService.save(task);
            logger.info("任务创建成功，任务ID：{}", task.getId());

            // 转换为VO返回
            TaskVO taskVO = new TaskVO();
            BeanUtils.copyProperties(task, taskVO);
            return Result.success(taskVO);
        } catch (Exception e) {
            logger.error("创建任务失败，任务名称：{}", taskCreateDTO.getName(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取所有任务列表
     *
     * @return 任务列表
     */
    @GetMapping
    @Operation(summary = "获取任务列表", description = "获取所有任务的列表")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取任务列表成功", 
                     content = @Content(schema = @Schema(implementation = TaskVO.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<TaskVO>> listTasks() {
        logger.info("开始获取任务列表");
        try {
            List<Task> tasks = taskService.list();
            logger.info("获取任务列表成功，共{}个任务", tasks.size());
            List<TaskVO> taskVOs = tasks.stream()
                    .map(task -> {
                        TaskVO taskVO = new TaskVO();
                        BeanUtils.copyProperties(task, taskVO);
                        return taskVO;
                    })
                    .collect(Collectors.toList());
            return Result.success(taskVOs);
        } catch (Exception e) {
            logger.error("获取任务列表失败", e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 根据ID获取任务详情
     *
     * @param id 任务ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情", description = "根据任务ID获取任务的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取任务详情成功", 
                     content = @Content(schema = @Schema(implementation = TaskVO.class))),
        @ApiResponse(responseCode = "400", description = "任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<TaskVO> getTaskById(@Parameter(description = "任务ID", required = true, example = "123456") 
                                     @PathVariable String id) {
        logger.info("开始获取任务详情，任务ID：{}", id);
        try {
            Task task = taskService.getById(id);
            if (task == null) {
                logger.warn("任务不存在，任务ID：{}", id);
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }
            logger.info("获取任务详情成功，任务ID：{}", id);
            TaskVO taskVO = new TaskVO();
            BeanUtils.copyProperties(task, taskVO);
            return Result.success(taskVO);
        } catch (Exception e) {
            logger.error("获取任务详情失败，任务ID：{}", id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 更新任务
     *
     * @param taskUpdateDTO 任务更新请求DTO
     * @return 更新后的任务信息
     */
    @PutMapping
    @Operation(summary = "更新任务", description = "更新现有任务的信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务更新成功", 
                     content = @Content(schema = @Schema(implementation = TaskVO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<TaskVO> updateTask(@Valid @RequestBody TaskUpdateDTO taskUpdateDTO) {
        logger.info("开始更新任务，任务ID：{}", taskUpdateDTO.getId());
        try {
            // 检查任务是否存在
            Task existingTask = taskService.getById(taskUpdateDTO.getId());
            if (existingTask == null) {
                logger.warn("任务不存在，任务ID：{}", taskUpdateDTO.getId());
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }

            // 更新任务信息
            BeanUtils.copyProperties(taskUpdateDTO, existingTask, "id", "enabled", "status", "createdBy", "createTime");
            existingTask.setUpdateTime(LocalDateTime.now());
            taskService.updateById(existingTask);
            logger.info("任务更新成功，任务ID：{}", taskUpdateDTO.getId());

            // 转换为VO返回
            TaskVO taskVO = new TaskVO();
            BeanUtils.copyProperties(existingTask, taskVO);
            return Result.success(taskVO);
        } catch (Exception e) {
            logger.error("更新任务失败，任务ID：{}", taskUpdateDTO.getId(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 删除任务
     *
     * @param id 任务ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务", description = "软删除任务，将任务状态更新为deleted")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务删除成功", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Void> deleteTask(@Parameter(description = "任务ID", required = true, example = "123456") 
                                   @PathVariable String id) {
        logger.info("开始删除任务，任务ID：{}", id);
        try {
            // 检查任务是否存在
            Task existingTask = taskService.getById(id);
            if (existingTask == null) {
                logger.warn("任务不存在，任务ID：{}", id);
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }

            // 软删除：更新任务状态为deleted
            existingTask.setStatus(TaskStatus.deleted);
            existingTask.setEnabled(false);
            existingTask.setUpdateTime(LocalDateTime.now());
            taskService.updateById(existingTask);
            logger.info("任务删除成功，任务ID：{}", id);

            return Result.success();
        } catch (Exception e) {
            logger.error("删除任务失败，任务ID：{}", id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 启用任务（仅适用于定时任务）
     *
     * @param id 任务ID
     * @return 启用结果
     */
    @PutMapping("/{id}/enable")
    @Operation(summary = "启用任务", description = "启用定时任务，使其可以按照设定的Cron表达式执行")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务启用成功", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Void> enableTask(@Parameter(description = "任务ID", required = true, example = "123456") 
                                  @PathVariable String id) {
        logger.info("开始启用任务，任务ID：{}", id);
        try {
            Task task = taskService.getById(id);
            if (task == null) {
                logger.warn("任务不存在，任务ID：{}", id);
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }

            task.setEnabled(true);
            task.setUpdateTime(LocalDateTime.now());
            taskService.updateById(task);
            logger.info("任务启用成功，任务ID：{}", id);
            return Result.success();
        } catch (Exception e) {
            logger.error("启用任务失败，任务ID：{}", id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 禁用任务（仅适用于定时任务）
     *
     * @param id 任务ID
     * @return 禁用结果
     */
    @PutMapping("/{id}/disable")
    @Operation(summary = "禁用任务", description = "禁用定时任务，使其停止按照设定的Cron表达式执行")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务禁用成功", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Void> disableTask(@Parameter(description = "任务ID", required = true, example = "123456") 
                                   @PathVariable String id) {
        logger.info("开始禁用任务，任务ID：{}", id);
        try {
            Task task = taskService.getById(id);
            if (task == null) {
                logger.warn("任务不存在，任务ID：{}", id);
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }

            task.setEnabled(false);
            task.setUpdateTime(LocalDateTime.now());
            taskService.updateById(task);
            logger.info("任务禁用成功，任务ID：{}", id);
            return Result.success();
        } catch (Exception e) {
            logger.error("禁用任务失败，任务ID：{}", id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 发布任务
     * 立即执行一次任务
     *
     * @param id 任务ID
     * @return 发布结果
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布任务", description = "立即执行一次任务，无论任务类型是一次性还是定时")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "任务发布成功", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "400", description = "任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Void> publishTask(@Parameter(description = "任务ID", required = true, example = "123456") 
                                   @PathVariable String id) {
        logger.info("开始发布任务，任务ID：{}", id);
        try {
            // 检查任务是否存在
            Task task = taskService.getById(id);
            if (task == null) {
                logger.warn("任务不存在，任务ID：{}", id);
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }

            // 发布任务
            taskService.publishTask(id);
            logger.info("任务发布成功，任务ID：{}", id);
            return Result.success();
        } catch (Exception e) {
            logger.error("发布任务失败，任务ID：{}", id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
    
    /**
     * 获取任务执行历史
     * 查看任务的所有执行记录
     *
     * @param id 任务ID
     * @return 任务执行记录列表
     */
    @GetMapping("/{id}/runs")
    @Operation(summary = "获取任务执行历史", description = "查看指定任务的所有执行记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取任务执行历史成功", 
                     content = @Content(schema = @Schema(implementation = TaskRun.class))),
        @ApiResponse(responseCode = "400", description = "任务不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<TaskRun>> getTaskRuns(@Parameter(description = "任务ID", required = true, example = "123456") 
                                             @PathVariable String id) {
        logger.info("开始获取任务执行历史，任务ID：{}", id);
        try {
            // 检查任务是否存在
            Task task = taskService.getById(id);
            if (task == null) {
                logger.warn("任务不存在，任务ID：{}", id);
                return Result.error(ErrorCodeEnum.TASK_NOT_FOUND);
            }
            
            // 查询任务执行记录
            List<TaskRun> taskRuns = taskRunMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskRun>()
                            .eq(TaskRun::getTaskId, id)
                            .orderByDesc(TaskRun::getCreateTime)
            );
            logger.info("获取任务执行历史成功，任务ID：{}，共{}条记录", id, taskRuns.size());
            return Result.success(taskRuns);
        } catch (Exception e) {
            logger.error("获取任务执行历史失败，任务ID：{}", id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
    
    /**
     * 获取任务执行记录详情
     * 查看特定执行记录的详细信息
     *
     * @param runId 任务执行记录ID
     * @return 任务执行记录详情
     */
    @GetMapping("/runs/{runId}")
    @Operation(summary = "获取任务执行记录详情", description = "查看特定任务执行记录的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取任务执行记录详情成功", 
                     content = @Content(schema = @Schema(implementation = TaskRun.class))),
        @ApiResponse(responseCode = "400", description = "执行记录不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<TaskRun> getTaskRunDetail(@Parameter(description = "任务执行记录ID", required = true, example = "123456") 
                                           @PathVariable String runId) {
        logger.info("开始获取任务执行记录详情，执行记录ID：{}", runId);
        try {
            // 查询执行记录
            TaskRun taskRun = taskRunMapper.selectById(runId);
            if (taskRun == null) {
                logger.warn("执行记录不存在，执行记录ID：{}", runId);
                return Result.error(ErrorCodeEnum.TASK_RUN_NOT_FOUND);
            }
            logger.info("获取任务执行记录详情成功，执行记录ID：{}", runId);
            return Result.success(taskRun);
        } catch (Exception e) {
            logger.error("获取任务执行记录详情失败，执行记录ID：{}", runId, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
    
    /**
     * 获取任务执行日志
     * 查看任务执行日志信息
     *
     * @param runId 任务执行记录ID
     * @return 任务执行日志信息
     */
    @GetMapping("/runs/{runId}/log")
    @Operation(summary = "获取任务执行日志", description = "查看特定任务执行记录的日志信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取任务执行日志成功", 
                     content = @Content(schema = @Schema(implementation = TaskLog.class))),
        @ApiResponse(responseCode = "400", description = "执行记录不存在或日志不存在", 
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误", 
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<TaskLog> getTaskRunLog(@Parameter(description = "任务执行记录ID", required = true, example = "123456") 
                                        @PathVariable String runId) {
        logger.info("开始获取任务执行日志，执行记录ID：{}", runId);
        try {
            // 查询日志记录
            TaskLog taskLog = taskLogMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                            .eq(TaskLog::getRunId, runId)
            );
            if (taskLog == null) {
                logger.warn("日志记录不存在，执行记录ID：{}", runId);
                return Result.error(ErrorCodeEnum.TASK_LOG_NOT_FOUND);
            }
            logger.info("获取任务执行日志成功，执行记录ID：{}", runId);
            return Result.success(taskLog);
        } catch (Exception e) {
            logger.error("获取任务执行日志失败，执行记录ID：{}", runId, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }
    
    /**
     * 下载任务执行日志文件
     * 下载任务执行的日志文件
     *
     * @param runId 任务执行记录ID
     * @param response HTTP响应
     */
    @GetMapping("/runs/{runId}/log/download")
    @Operation(summary = "下载任务执行日志文件", description = "下载特定任务执行记录的日志文件")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "下载任务执行日志文件成功"),
        @ApiResponse(responseCode = "400", description = "执行记录不存在或日志不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public void downloadTaskRunLog(@Parameter(description = "任务执行记录ID", required = true, example = "123456") 
                                 @PathVariable String runId, 
                                 HttpServletResponse response) {
        logger.info("开始下载任务执行日志文件，执行记录ID：{}", runId);
        try {
            // 查询日志记录
            TaskLog taskLog = taskLogMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskLog>()
                            .eq(TaskLog::getRunId, runId)
            );
            if (taskLog == null) {
                logger.warn("日志记录不存在，执行记录ID：{}", runId);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            
            // 解析日志路径，格式：bucketName/objectName
            String logPath = taskLog.getStdoutPath();
            logger.info("日志文件路径：{}", logPath);
            
            // 分割桶名和文件名
            int firstSlashIndex = logPath.indexOf('/');
            if (firstSlashIndex == -1) {
                logger.error("日志路径格式错误，执行记录ID：{}，路径：{}", runId, logPath);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            String bucketName = logPath.substring(0, firstSlashIndex);
            String objectName = logPath.substring(firstSlashIndex + 1);
            logger.info("解析日志路径成功，桶名：{}，文件名：{}", bucketName, objectName);
            
            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=task_log_" + runId + ".log");
            
            // 从MinIO下载日志文件
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            ); ServletOutputStream outputStream = response.getOutputStream()) {
                // 将文件写入响应流
                byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
            
            logger.info("下载任务执行日志文件成功，执行记录ID：{}", runId);
        } catch (Exception e) {
            logger.error("下载任务执行日志文件失败，执行记录ID：{}", runId, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                logger.error("设置响应状态失败", ex);
            }
        }
    }
}
