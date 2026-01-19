package com.sjdt.scheduler.controller;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.task.TaskCallbackExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务健康检查Controller
 * 提供任务和回调配置的健康检查功能
 */
@Slf4j
@RestController
@RequestMapping("/api")
@io.swagger.v3.oas.annotations.tags.Tag(name = "任务健康检查", description = "任务和回调配置的健康检查功能")
public class HealthCheckController {

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskCallbackExecutor taskCallbackExecutor;

    @Autowired
    private RestTemplate restTemplate;

    private final CronParser cronParser;

    public HealthCheckController() {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX);
        this.cronParser = new CronParser(cronDefinition);
    }

    /**
     * 回调任务配置健康检查
     * 检查回调URL的连通性和Cron表达式的合理性
     */
    @PostMapping("/callback-job-configs/{id}/health-check")
    @Operation(summary = "回调任务配置健康检查", description = "检查回调URL的连通性和Cron表达式的合理性")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "健康检查成功",
                     content = @Content(schema = @Schema(implementation = HealthCheckResult.class))),
        @ApiResponse(responseCode = "400", description = "配置不存在或参数错误",
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "健康检查失败",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<HealthCheckResult> checkCallbackConfigHealth(
            @Parameter(description = "回调配置ID", required = true, example = "config-001")
            @PathVariable String id) {
        log.info("开始回调任务配置健康检查，配置ID：{}", id);

        try {
            CallbackJobConfig config = callbackJobConfigMapper.selectById(id);
            if (config == null) {
                log.warn("回调配置不存在，配置ID：{}", id);
                return Result.error(404, "回调配置不存在");
            }

            HealthCheckResult result = new HealthCheckResult();
            result.setConfigId(id);
            result.setCallbackUrl(config.getCallbackUrl());
            result.setCheckTime(LocalDateTime.now());

            // 检查Cron表达式（如果有关联任务）
            Task task = taskMapper.selectById(config.getTaskId());
            if (task != null && task.getCronExpr() != null && !task.getCronExpr().trim().isEmpty()) {
                CronValidationResult cronResult = validateCronExpression(task.getCronExpr());
                result.setCronExpr(task.getCronExpr());
                result.setCronValid(cronResult.isValid());
                result.setCronDescription(cronResult.getDescription());
            }

            // 检查回调URL健康状况
            HealthCheckResult urlCheckResult = checkCallbackUrlHealth(config);
            result.setUrlHealthy(urlCheckResult.isUrlHealthy());
            result.setUrlResponseCode(urlCheckResult.getUrlResponseCode());
            result.setUrlResponseTimeMs(urlCheckResult.getUrlResponseTimeMs());
            result.setUrlErrorMessage(urlCheckResult.getUrlErrorMessage());

            // 总体健康状况
            boolean overallHealthy = result.isUrlHealthy() && 
                                  (result.getCronExpr() == null || result.getCronValid());
            result.setOverallHealthy(overallHealthy);

            log.info("回调任务配置健康检查完成，配置ID：{}，总体健康：{}",
                id, overallHealthy);

            return Result.success(result);

        } catch (Exception e) {
            log.error("回调任务配置健康检查异常，配置ID：{}", id, e);
            return Result.error(500, "健康检查失败：" + e.getMessage());
        }
    }

    /**
     * 验证Cron表达式的合理性
     *
     * @param cronExpr Cron表达式
     * @return 验证结果
     */
    private CronValidationResult validateCronExpression(String cronExpr) {
        CronValidationResult result = new CronValidationResult();
        result.setValid(true);
        result.setDescription("Cron表达式格式正确");

        try {
            Cron cron = cronParser.parse(cronExpr);
            ZonedDateTime now = LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai"));
            
            // 尝试计算下一次执行时间
            try {
                com.cronutils.model.time.ExecutionTime executionTime = 
                    com.cronutils.model.time.ExecutionTime.forCron(cron);
                
                if (executionTime.nextExecution(now).isPresent()) {
                    result.setDescription("Cron表达式格式正确，下次执行时间：" + 
                                       executionTime.nextExecution(now).get());
                }
            } catch (Exception e) {
                result.setValid(false);
                result.setDescription("Cron表达式无法计算下次执行时间：" + e.getMessage());
            }

        } catch (Exception e) {
            result.setValid(false);
            result.setDescription("Cron表达式格式错误：" + e.getMessage());
        }

        return result;
    }

    /**
     * 检查回调URL的健康状况
     * 向回调URL + "/health" 发送GET请求进行健康检查
     *
     * @param config 回调配置
     * @return 健康检查结果
     */
    private HealthCheckResult checkCallbackUrlHealth(CallbackJobConfig config) {
        HealthCheckResult result = new HealthCheckResult();

        try {
            String healthUrl = buildHealthCheckUrl(config.getCallbackUrl());
            log.debug("开始检查回调URL健康状况，目标URL：{}", healthUrl);

            long startTime = System.currentTimeMillis();

            // 构建请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Request-Id", generateRequestId());
            headers.put("X-Secret", config.getSecretKey());

            // 发送健康检查请求
            org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
            headers.forEach(httpHeaders::add);

            org.springframework.http.HttpEntity<Void> requestEntity = 
                new org.springframework.http.HttpEntity<>(httpHeaders);

            ResponseEntity<String> response = restTemplate.exchange(
                healthUrl,
                HttpMethod.GET,
                requestEntity,
                String.class
            );

            long responseTime = System.currentTimeMillis() - startTime;

            result.setUrlHealthy(response.getStatusCode().is2xxSuccessful());
            result.setUrlResponseCode(response.getStatusCode().value());
            result.setUrlResponseTimeMs((int) responseTime);
            result.setUrlErrorMessage(null);

            log.debug("回调URL健康检查完成，URL：{}，状态码：{}，响应时间：{}ms",
                healthUrl, response.getStatusCode(), responseTime);

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - 
                              (System.currentTimeMillis() - 10000); // 估算

            result.setUrlHealthy(false);
            result.setUrlResponseCode(null);
            result.setUrlResponseTimeMs((int) responseTime);
            result.setUrlErrorMessage(e.getMessage());

            log.warn("回调URL健康检查失败，URL：{}，错误：{}", config.getCallbackUrl(), e.getMessage());
        }

        return result;
    }

    /**
     * 构建健康检查URL
     * 在回调URL后追加 "/health"
     *
     * @param callbackUrl 回调URL
     * @return 健康检查URL
     */
    private String buildHealthCheckUrl(String callbackUrl) {
        if (callbackUrl.endsWith("/")) {
            return callbackUrl + "health";
        } else {
            return callbackUrl + "/health";
        }
    }

    /**
     * 生成请求ID
     *
     * @return 请求ID
     */
    private String generateRequestId() {
        return "health-check-" + System.currentTimeMillis() + "-" + 
               Thread.currentThread().getId();
    }

    /**
     * 健康检查结果
     */
    @Data
    public static class HealthCheckResult {
        /** 配置ID */
        private String configId;

        /** 回调URL */
        private String callbackUrl;

        /** 检查时间 */
        private LocalDateTime checkTime;

        /** 总体健康状况 */
        private boolean overallHealthy;

        /** URL健康状况 */
        private boolean urlHealthy;

        /** URL响应状态码 */
        private Integer urlResponseCode;

        /** URL响应时间（毫秒） */
        private Integer urlResponseTimeMs;

        /** URL错误信息 */
        private String urlErrorMessage;

        /** Cron表达式 */
        private String cronExpr;

        /** Cron表达式是否有效 */
        private Boolean cronValid;

        /** Cron表达式描述 */
        private String cronDescription;
    }

    /**
     * Cron表达式验证结果
     */
    @Data
    public static class CronValidationResult {
        /** 是否有效 */
        private boolean valid;

        /** 描述信息 */
        private String description;
    }
}
