package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.cronova.scheduler.common.ErrorCodeEnum;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.dto.CallbackJobRunQueryDTO;
import com.cronova.scheduler.domain.vo.CallbackJobRunVO;
import com.cronova.scheduler.service.ICallbackJobRunService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 回调执行记录控制器
 * 提供回调执行记录的查询、统计和数据获取功能
 */
@RestController
@RequestMapping("/api/callback-job-runs")
@SaCheckLogin
@Tag(name = "回调执行记录管理", description = "回调执行记录的查询、统计、数据获取和重试操作")
@Slf4j
public class CallbackJobRunController {

    private final ICallbackJobRunService callbackJobRunService;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String BUCKET_NAME;

    @Autowired
    public CallbackJobRunController(ICallbackJobRunService callbackJobRunService, MinioClient minioClient) {
        this.callbackJobRunService = callbackJobRunService;
        this.minioClient = minioClient;
    }

    /**
     * 根据ID查询执行记录详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取执行记录详情", description = "根据执行记录ID获取回调执行记录的详细信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功",
                     content = @Content(schema = @Schema(implementation = CallbackJobRunVO.class))),
        @ApiResponse(responseCode = "404", description = "执行记录不存在",
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CallbackJobRunVO> getById(
            @Parameter(description = "执行记录ID", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 查询执行记录详情，ID: {}", userId, id);

        CallbackJobRunVO vo = callbackJobRunService.getDetailById(id);
        if (vo == null) {
            log.warn("[{}] 执行记录不存在，ID: {}", userId, id);
            return Result.error(404, "CallbackJobRun not found");
        }

        return Result.success(vo);
    }

    /**
     * 根据任务ID查询执行记录列表
     */
    @GetMapping("/task/{taskId}")
    @Operation(summary = "根据任务ID查询执行记录", description = "根据任务ID获取该任务关联的所有回调执行记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功",
                     content = @Content(array = @ArraySchema(schema = @Schema(implementation = CallbackJobRunVO.class)))),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<CallbackJobRunVO>> getByTaskId(
            @Parameter(description = "任务ID", required = true, example = "task-001")
            @PathVariable String taskId) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 根据任务ID查询执行记录，taskId: {}", userId, taskId);

        List<CallbackJobRunVO> list = callbackJobRunService.getByTaskId(taskId);
        log.info("[{}] 查询完成，taskId: {}，共{}条记录", userId, taskId, list.size());

        return Result.success(list);
    }

    /**
     * 根据配置ID查询执行记录列表
     */
    @GetMapping("/config/{configId}")
    @Operation(summary = "根据配置ID查询执行记录", description = "根据回调配置ID获取该配置关联的所有执行记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功",
                     content = @Content(array = @ArraySchema(schema = @Schema(implementation = CallbackJobRunVO.class)))),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<CallbackJobRunVO>> getByConfigId(
            @Parameter(description = "配置ID", required = true, example = "config-001")
            @PathVariable String configId) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 根据配置ID查询执行记录，configId: {}", userId, configId);

        List<CallbackJobRunVO> list = callbackJobRunService.getByConfigId(configId);
        log.info("[{}] 查询完成，configId: {}，共{}条记录", userId, configId, list.size());

        return Result.success(list);
    }

    /**
     * 根据request_id查询执行记录
     */
    @GetMapping("/request/{requestId}")
    @Operation(summary = "根据请求ID查询执行记录", description = "根据请求ID（X-Request-Id）获取回调执行记录，用于请求追踪")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功",
                     content = @Content(schema = @Schema(implementation = CallbackJobRunVO.class))),
        @ApiResponse(responseCode = "404", description = "执行记录不存在",
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CallbackJobRunVO> getByRequestId(
            @Parameter(description = "请求ID（X-Request-Id）", required = true, example = "abc123def456")
            @PathVariable String requestId) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 根据请求ID查询执行记录，requestId: {}", userId, requestId);

        CallbackJobRunVO vo = callbackJobRunService.getByRequestId(requestId);
        if (vo == null) {
            log.warn("[{}] 执行记录不存在，requestId: {}", userId, requestId);
            return Result.error(404, "CallbackJobRun not found");
        }

        return Result.success(vo);
    }

    /**
     * 分页查询执行记录
     */
    @GetMapping
    @Operation(summary = "分页查询执行记录", description = "根据条件分页查询回调执行记录，支持多种筛选条件")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功，返回分页数据和分页信息"),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<?> pageQuery(
            @Parameter(description = "任务ID")
            @RequestParam(required = false) String taskId,

            @Parameter(description = "配置ID")
            @RequestParam(required = false) String configId,

            @Parameter(description = "任务运行实例ID")
            @RequestParam(required = false) String taskRunId,

            @Parameter(description = "请求ID")
            @RequestParam(required = false) String requestId,

            @Parameter(description = "执行状态")
            @RequestParam(required = false) String status,

            @Parameter(description = "是否只查询失败记录")
            @RequestParam(required = false, defaultValue = "false") Boolean onlyFailed,

            @Parameter(description = "开始时间范围起始（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) String startTimeFrom,

            @Parameter(description = "开始时间范围结束（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) String startTimeTo,

            @Parameter(description = "创建时间范围起始（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) String createTimeFrom,

            @Parameter(description = "创建时间范围结束（格式：yyyy-MM-dd'T'HH:mm:ss）")
            @RequestParam(required = false) String createTimeTo,

            @Parameter(description = "执行耗时最小值（毫秒）")
            @RequestParam(required = false) Integer durationMin,

            @Parameter(description = "执行耗时最大值（毫秒）")
            @RequestParam(required = false) Integer durationMax,

            @Parameter(description = "页码，从1开始")
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,

            @Parameter(description = "每页大小")
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,

            @Parameter(description = "排序字段：createTime/startTime/durationMs")
            @RequestParam(required = false, defaultValue = "createTime") String orderBy,

            @Parameter(description = "排序方向：asc/desc")
            @RequestParam(required = false, defaultValue = "desc") String orderDirection) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 分页查询执行记录，pageNum: {}，pageSize: {}", userId, pageNum, pageSize);

        CallbackJobRunQueryDTO queryDTO = new CallbackJobRunQueryDTO();
        queryDTO.setTaskId(taskId);
        queryDTO.setConfigId(configId);
        queryDTO.setTaskRunId(taskRunId);
        queryDTO.setRequestId(requestId);
        queryDTO.setStatus(status);
        queryDTO.setOnlyFailed(onlyFailed);
        queryDTO.setPageNum(pageNum);
        queryDTO.setPageSize(pageSize);
        queryDTO.setOrderBy(orderBy);
        queryDTO.setOrderDirection(orderDirection);

        if (startTimeFrom != null && !startTimeFrom.isEmpty()) {
            queryDTO.setStartTimeFrom(java.time.LocalDateTime.parse(startTimeFrom));
        }
        if (startTimeTo != null && !startTimeTo.isEmpty()) {
            queryDTO.setStartTimeTo(java.time.LocalDateTime.parse(startTimeTo));
        }
        if (createTimeFrom != null && !createTimeFrom.isEmpty()) {
            queryDTO.setCreateTimeFrom(java.time.LocalDateTime.parse(createTimeFrom));
        }
        if (createTimeTo != null && !createTimeTo.isEmpty()) {
            queryDTO.setCreateTimeTo(java.time.LocalDateTime.parse(createTimeTo));
        }

        queryDTO.setDurationMin(durationMin);
        queryDTO.setDurationMax(durationMax);

        return Result.successWithPage(
            callbackJobRunService.pageQuery(queryDTO).getRecords(),
            callbackJobRunService.pageQuery(queryDTO).getCurrent(),
            callbackJobRunService.pageQuery(queryDTO).getSize(),
            callbackJobRunService.pageQuery(queryDTO).getTotal(),
            callbackJobRunService.pageQuery(queryDTO).getPages()
        );
    }

    /**
     * 获取配置回调统计信息
     */
    @GetMapping("/config/{configId}/stats")
    @Operation(summary = "获取配置回调统计", description = "获取指定回调配置的统计信息，包括成功数、失败数、成功率等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Map<String, Object>> getConfigStats(
            @Parameter(description = "配置ID", required = true, example = "config-001")
            @PathVariable String configId) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 获取配置回调统计，configId: {}", userId, configId);

        Map<String, Object> stats = callbackJobRunService.getConfigStatistics(configId);
        return Result.success(stats);
    }

    /**
     * 获取全局回调统计信息
     */
    @GetMapping("/stats/overview")
    @Operation(summary = "获取全局回调统计", description = "获取系统所有回调执行的全局统计信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "500", description = "系统内部错误",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Map<String, Object>> getGlobalStats() {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 获取全局回调统计", userId);

        Map<String, Object> stats = callbackJobRunService.getGlobalStatistics();
        return Result.success(stats);
    }

    /**
     * 获取完整请求数据
     */
    @GetMapping("/{id}/request")
    @Operation(summary = "获取完整请求数据", description = "获取回调请求的完整数据，包括请求头和请求体")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "执行记录不存在或请求数据不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public Result<?> getRequestData(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 获取完整请求数据，id: {}", userId, id);

        CallbackJobRunVO vo = callbackJobRunService.getDetailById(id);
        if (vo == null) {
            return Result.error(404, "CallbackJobRun not found");
        }

        if (vo.getRequestPath() == null || vo.getRequestPath().isEmpty()) {
            return Result.error(404, "Request data not found");
        }

        try {
            String content = downloadFromMinio(vo.getRequestPath());
            return Result.success(content);
        } catch (Exception e) {
            log.error("[{}] 获取请求数据失败，id: {}", userId, id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取完整响应数据
     */
    @GetMapping("/{id}/response")
    @Operation(summary = "获取完整响应数据", description = "获取回调响应的完整数据，包括状态码、响应头和响应体")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "404", description = "执行记录不存在或响应数据不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public Result<?> getResponseData(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 获取完整响应数据，id: {}", userId, id);

        CallbackJobRunVO vo = callbackJobRunService.getDetailById(id);
        if (vo == null) {
            return Result.error(404, "CallbackJobRun not found");
        }

        if (vo.getResponsePath() == null || vo.getResponsePath().isEmpty()) {
            return Result.error(404, "Response data not found");
        }

        try {
            String content = downloadFromMinio(vo.getResponsePath());
            return Result.success(content);
        } catch (Exception e) {
            log.error("[{}] 获取响应数据失败，id: {}", userId, id, e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 下载请求数据文件
     */
    @GetMapping("/{id}/download/request")
    @Operation(summary = "下载请求数据文件", description = "下载回调请求的完整数据文件（JSON格式）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "下载成功"),
        @ApiResponse(responseCode = "404", description = "执行记录不存在或请求数据不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public void downloadRequestData(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String id,
            HttpServletResponse response) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 下载请求数据文件，id: {}", userId, id);

        try {
            CallbackJobRunVO vo = callbackJobRunService.getDetailById(id);
            if (vo == null || vo.getRequestPath() == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String content = downloadFromMinio(vo.getRequestPath());
            downloadFile(response, content, "request-" + id + ".json");

        } catch (Exception e) {
            log.error("[{}] 下载请求数据文件失败，id: {}", userId, id, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                log.error("设置响应状态失败", ex);
            }
        }
    }

    /**
     * 下载响应数据文件
     */
    @GetMapping("/{id}/download/response")
    @Operation(summary = "下载响应数据文件", description = "下载回调响应的完整数据文件（JSON格式）")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "下载成功"),
        @ApiResponse(responseCode = "404", description = "执行记录不存在或响应数据不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public void downloadResponseData(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String id,
            HttpServletResponse response) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 下载响应数据文件，id: {}", userId, id);

        try {
            CallbackJobRunVO vo = callbackJobRunService.getDetailById(id);
            if (vo == null || vo.getResponsePath() == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String content = downloadFromMinio(vo.getResponsePath());
            downloadFile(response, content, "response-" + id + ".json");

        } catch (Exception e) {
            log.error("[{}] 下载响应数据文件失败，id: {}", userId, id, e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            } catch (Exception ex) {
                log.error("设置响应状态失败", ex);
            }
        }
    }

    /**
     * 手动重试回调
     */
    @PostMapping("/{id}/retry")
    @Operation(summary = "手动重试回调", description = "手动触发对失败回调的重试")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "重试指令已发送"),
        @ApiResponse(responseCode = "404", description = "执行记录不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public Result<Boolean> retry(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 手动重试回调，id: {}", userId, id);

        boolean success = callbackJobRunService.retry(id);
        if (success) {
            return Result.success(true);
        } else {
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 删除执行记录
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除执行记录", description = "删除指定的回调执行记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "404", description = "执行记录不存在"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public Result<Void> delete(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 删除执行记录，id: {}", userId, id);

        boolean success = callbackJobRunService.deleteById(id);
        if (success) {
            return Result.success();
        } else {
            return Result.error(404, "CallbackJobRun not found");
        }
    }

    /**
     * 根据任务ID删除执行记录
     */
    @DeleteMapping("/task/{taskId}")
    @Operation(summary = "根据任务ID删除执行记录", description = "删除指定任务关联的所有回调执行记录")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "500", description = "系统内部错误")
    })
    public Result<Integer> deleteByTaskId(
            @Parameter(description = "任务ID", required = true)
            @PathVariable String taskId) {
        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 根据任务ID删除执行记录，taskId: {}", userId, taskId);

        int count = callbackJobRunService.deleteByTaskId(taskId);
        log.info("[{}] 删除完成，taskId: {}，共删除{}条记录", userId, taskId, count);

        return Result.success(count);
    }

    /**
     * 从MinIO下载文件内容
     */
    private String downloadFromMinio(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }

        // 统一使用配置的桶名：路径以"桶名/"开头时剥离桶名前缀，兼容旧数据中带桶名的路径
        String objectName = path;
        if (path.startsWith(BUCKET_NAME + "/")) {
            objectName = path.substring(BUCKET_NAME.length() + 1);
        } else if (path.contains("/")) {
            int firstSlashIndex = path.indexOf('/');
            String potentialBucket = path.substring(0, firstSlashIndex);
            if (!potentialBucket.equals(BUCKET_NAME)) {
                log.warn("检测到路径中的桶名 {} 与配置的 {} 不一致，尝试使用配置的桶名", potentialBucket, BUCKET_NAME);
            }
        }

        log.info("从 MinIO 下载数据，Bucket: {}, Object: {}", BUCKET_NAME, objectName);

        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(BUCKET_NAME)
                        .object(objectName)
                        .build());
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            return outputStream.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * 下载文件
     */
    private void downloadFile(HttpServletResponse response, String content, String fileName) throws IOException {
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()));
        response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }
}
