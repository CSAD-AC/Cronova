package com.sjdt.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.sjdt.scheduler.common.ErrorCodeEnum;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.vo.CallbackJobRunVO;
import com.sjdt.scheduler.service.ICallbackJobConfigService;
import com.sjdt.scheduler.service.ICallbackJobRunService;
import com.sjdt.scheduler.task.TaskCallbackExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回调任务测试控制器
 * 【注意】此控制器仅用于开发和调试，与正常业务逻辑分离
 *
 * 区分标识：
 * - 测试回调的 requestId 以 "TEST-" 开头
 * - 测试回调的 taskRunId 以 "TEST-" 开头
 * - 测试回调的 retryIndex 为 -1
 *
 * 【核心说明】
 * 测试接口直接调用真实的回调业务流程：
 * 1. 读取回调配置（URL、方法、密钥等）
 * 2. 创建执行记录（标记为测试）
 * 3. 存储请求数据到 MinIO
 * 4. 发送真实的 HTTP 请求到回调接口
 * 5. 存储响应数据到 MinIO
 * 6. 更新执行记录状态和统计信息
 */
@RestController
@RequestMapping("/api/callback-test")
@SaCheckLogin
@Tag(name = "回调任务测试", description = "回调任务调试接口，仅用于开发和测试环境，直接执行真实的回调业务流程")
@Slf4j
public class CallbackTestController {

    private final TaskCallbackExecutor taskCallbackExecutor;
    private final ICallbackJobConfigService callbackJobConfigService;
    private final ICallbackJobRunService callbackJobRunService;

    @Autowired
    public CallbackTestController(TaskCallbackExecutor taskCallbackExecutor,
                                  ICallbackJobConfigService callbackJobConfigService,
                                  ICallbackJobRunService callbackJobRunService) {
        this.taskCallbackExecutor = taskCallbackExecutor;
        this.callbackJobConfigService = callbackJobConfigService;
        this.callbackJobRunService = callbackJobRunService;
    }

    /**
     * 手动触发测试回调
     *
     * 【核心功能】
     * 直接执行真实的回调业务流程，与正常任务完成后的回调完全一致
     * 区别仅在于使用测试标记，便于区分和调试
     *
     * 【执行流程】
     * 1. 根据 configId 获取回调配置
     * 2. 创建执行记录（标记为测试，retryIndex = -1）
     * 3. 存储请求数据到 MinIO
     * 4. 发送真实的 HTTP 请求到配置的回调 URL
     * 5. 存储响应数据到 MinIO
     * 6. 更新执行记录状态和统计信息
     */
    @PostMapping("/trigger/{configId}")
    @Operation(summary = "手动触发测试回调", description = "根据配置ID直接执行真实的回调业务流程，用于调试和测试回调功能是否正常工作")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "测试回调已触发，返回执行记录ID",
                     content = @Content(schema = @Schema(implementation = Map.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或配置不存在",
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "回调执行失败（HTTP请求失败或超时）",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<Map<String, Object>> triggerTestCallback(
            @Parameter(description = "回调配置ID", required = true, example = "config-001")
            @PathVariable String configId,

            @Parameter(description = "模拟的任务执行结果，将发送给回调接口", example = "success")
            @RequestParam(defaultValue = "success") String taskResult) {

        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 【测试回调】手动触发回调，配置ID: {}，任务结果: {}", userId, configId, taskResult);

        try {
            // 执行真正的回调流程
            String runId = taskCallbackExecutor.executeTestCallback(configId, taskResult);

            if (runId == null) {
                return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("runId", runId);
            result.put("configId", configId);
            result.put("taskResult", taskResult);
            result.put("isTest", true);
            result.put("message", "测试回调已执行完成，请查询执行记录获取详细信息");

            log.info("[{}] 【测试回调】触发成功，runId: {}", userId, runId);
            return Result.success(result);

        } catch (IllegalArgumentException e) {
            log.warn("[{}] 【测试回调】参数错误: {}", userId, e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] 【测试回调】执行失败: {}", userId, e.getMessage(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 批量触发测试回调
     *
     * 根据任务ID触发该任务下所有回调配置的测试
     */
    @PostMapping("/trigger-batch")
    @Operation(summary = "批量触发测试回调", description = "根据任务ID触发该任务下所有回调配置的测试，执行真实的HTTP请求")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "批量测试回调已触发",
                     content = @Content(array = @ArraySchema(schema = @Schema(implementation = Map.class)))),
        @ApiResponse(responseCode = "400", description = "参数错误或任务没有配置回调",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<List<Map<String, Object>>> triggerBatchTestCallback(
            @Parameter(description = "任务ID", required = true, example = "task-001")
            @RequestParam String taskId,

            @Parameter(description = "模拟的任务执行结果", example = "success")
            @RequestParam(defaultValue = "success") String taskResult) {

        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 【测试回调】批量触发回调，任务ID: {}，任务结果: {}", userId, taskId, taskResult);

        try {
            List<CallbackJobConfig> configs = callbackJobConfigService.getByTaskId(taskId);

            if (configs.isEmpty()) {
                log.warn("[{}] 【测试回调】任务没有配置回调，taskId: {}", userId, taskId);
                return Result.error(400, "任务没有配置回调");
            }

            List<Map<String, Object>> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (CallbackJobConfig config : configs) {
                try {
                    String runId = taskCallbackExecutor.executeTestCallback(config.getId(), taskResult);

                    Map<String, Object> result = new HashMap<>();
                    result.put("configId", config.getId());
                    result.put("callbackUrl", config.getCallbackUrl());
                    result.put("runId", runId);
                    result.put("status", runId != null ? "success" : "failed");
                    results.add(result);

                    if (runId != null) {
                        successCount++;
                    } else {
                        failCount++;
                    }

                } catch (Exception e) {
                    Map<String, Object> result = new HashMap<>();
                    result.put("configId", config.getId());
                    result.put("callbackUrl", config.getCallbackUrl());
                    result.put("status", "failed");
                    result.put("error", e.getMessage());
                    results.add(result);
                    failCount++;
                }
            }

            log.info("[{}] 【测试回调】批量触发完成，成功: {}，失败: {}", userId, successCount, failCount);

            Map<String, Object> summary = new HashMap<>();
            summary.put("total", configs.size());
            summary.put("success", successCount);
            summary.put("failed", failCount);
            summary.put("results", results);

            return Result.success(results);

        } catch (Exception e) {
            log.error("[{}] 【测试回调】批量触发失败: {}", userId, e.getMessage(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取测试回调记录列表
     * 通过 retryIndex = -1 标识测试回调
     */
    @GetMapping("/runs")
    @Operation(summary = "获取测试回调记录", description = "获取所有测试回调的执行记录，用于区分测试和正常业务")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
                     content = @Content(array = @ArraySchema(schema = @Schema(implementation = CallbackJobRunVO.class))))
    })
    public Result<?> getTestRuns(
            @Parameter(description = "任务ID（可选）")
            @RequestParam(required = false) String taskId,

            @Parameter(description = "配置ID（可选）")
            @RequestParam(required = false) String configId,

            @Parameter(description = "页码")
            @RequestParam(defaultValue = "1") Integer pageNum,

            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") Integer pageSize) {

        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 【测试回调】查询测试记录，taskId: {}, configId: {}", userId, taskId, configId);

        try {
            var pageResult = callbackJobRunService.getTestRuns(taskId, configId, pageNum, pageSize);

            return Result.successWithPage(
                pageResult.getRecords(),
                pageResult.getCurrent(),
                pageResult.getSize(),
                pageResult.getTotal(),
                pageResult.getPages()
            );

        } catch (Exception e) {
            log.error("[{}] 【测试回调】查询失败: {}", userId, e.getMessage(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 获取测试回调详情
     */
    @GetMapping("/runs/{runId}")
    @Operation(summary = "获取测试回调详情", description = "根据执行记录ID获取测试回调的详细信息，包括请求和响应数据")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "获取成功",
                     content = @Content(schema = @Schema(implementation = CallbackJobRunVO.class))),
        @ApiResponse(responseCode = "404", description = "记录不存在",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CallbackJobRunVO> getTestRunDetail(
            @Parameter(description = "执行记录ID", required = true)
            @PathVariable String runId) {

        String userId = StpUtil.getLoginIdAsString();
        log.info("[{}] 【测试回调】查询记录详情，runId: {}", userId, runId);

        try {
            var run = callbackJobRunService.getDetailById(runId);

            if (run == null) {
                return Result.error(404, "执行记录不存在");
            }

            return Result.success(run);

        } catch (Exception e) {
            log.error("[{}] 【测试回调】查询失败: {}", userId, e.getMessage(), e);
            return Result.error(ErrorCodeEnum.SYSTEM_ERROR);
        }
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查回调测试服务是否正常可用")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "服务正常",
                     content = @Content(schema = @Schema(implementation = Map.class)))
    })
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "callback-test");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("note", "此接口仅用于测试和调试，直接执行真实的HTTP回调请求");
        return Result.success(health);
    }
}
