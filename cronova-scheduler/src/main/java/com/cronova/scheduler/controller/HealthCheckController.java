package com.cronova.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.field.CronField;
import com.cronutils.model.field.CronFieldName;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.cronova.scheduler.common.ErrorCodeEnum;
import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.mapper.CallbackJobConfigMapper;
import com.cronova.scheduler.mapper.TaskMapper;
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
    private RestTemplate restTemplate;

    private final CronParser cronParser;

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    public HealthCheckController() {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        this.cronParser = new CronParser(cronDefinition);
    }

    /**
     * 转换Cron表达式为中文描述（返回中文含义和下次执行时间）
     */
    @GetMapping("/cron/description")
    @Operation(summary = "Cron表达式转中文描述", description = "接受cron表达式，返回中文含义和下次执行时间")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "转换成功",
                     content = @Content(schema = @Schema(implementation = CronDescriptionResult.class))),
        @ApiResponse(responseCode = "400", description = "Cron表达式不合法",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<CronDescriptionResult> getCronDescription(
            @Parameter(description = "Cron表达式", required = true, example = "0 0 12 * * ?")
            @RequestParam String cronExpr) {
        log.info("转换Cron表达式为中文描述：{}", cronExpr);
        
        if (cronExpr == null || cronExpr.trim().isEmpty()) {
            return Result.error(400, "Cron表达式不能为空");
        }
        
        CronValidationResult cronResult = validateCronExpression(cronExpr);
        if (!cronResult.isValid()) {
            log.warn("Cron表达式不合法：{}，错误信息：{}", cronExpr, cronResult.getDescription());
            return Result.error(400, "Cron表达式不合法：" + cronResult.getDescription());
        }
        
        CronDescriptionResult result = new CronDescriptionResult();
        result.setCronExpr(cronExpr);
        result.setDescription(cronResult.getDescription());
        result.setNextExecutionTime(cronResult.getNextExecutionTime());
        
        log.info("Cron转换成功：{} -> {}", cronExpr, cronResult.getDescription());
        return Result.success(result);
    }

    /**
     * 回调任务配置健康检查：校验Cron合法性，并请求健康检查地址验证回调服务可用性
     */
    @PostMapping("/callback-job-configs/{id}/health-check")
    @SaCheckRole("admin")
    @Operation(summary = "回调任务配置健康检查", description = "检验Cron表达式合法性，并请求健康检查地址验证回调服务可用性")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "健康检查成功",
                     content = @Content(schema = @Schema(implementation = HealthCheckResult.class))),
        @ApiResponse(responseCode = "400", description = "Cron表达式不合法",
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "404", description = "配置不存在",
                     content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(responseCode = "500", description = "健康检查失败",
                     content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Result<HealthCheckResult> checkCallbackConfigHealth(
            @Parameter(description = "回调配置ID", required = true, example = "config-001")
            @PathVariable String id) {
        log.info("开始回调任务配置健康检查，配置ID：{}", id);

        CallbackJobConfig config = callbackJobConfigMapper.selectById(id);
        if (config == null) {
            log.warn("回调配置不存在，配置ID：{}", id);
            return Result.error(ErrorCodeEnum.CALLBACK_JOB_NOT_FOUND.getCode(), "回调配置不存在");
        }

        String callbackUrl = config.getCallbackUrl();
        String secretKey = config.getSecretKey();

        Task task = taskMapper.selectById(config.getTaskId());

        HealthCheckResult result = new HealthCheckResult();
        result.setConfigId(id);
        result.setCallbackUrl(callbackUrl);
        result.setCheckTime(LocalDateTime.now());

        if (task != null && task.getCronExpr() != null && !task.getCronExpr().trim().isEmpty()) {
            String cronExpr = task.getCronExpr();
            log.info("检测到Cron表达式：{}，开始验证", cronExpr);

            CronValidationResult cronResult = validateCronExpression(cronExpr);
            if (!cronResult.isValid()) {
                log.warn("Cron表达式不合法：{}，错误信息：{}", cronExpr, cronResult.getDescription());
                return Result.error(400, "Cron表达式不合法：" + cronResult.getDescription());
            }
            
            result.setCronExpr(cronExpr);
            result.setCronDescription(cronResult.getDescription());
            result.setCronNextExecutionTime(cronResult.getNextExecutionTime());
            log.info("Cron表达式验证通过，中文含义：{}，下次执行时间：{}", 
                cronResult.getDescription(), cronResult.getNextExecutionTime());
        }

        String healthUrl = buildHealthCheckUrl(callbackUrl);
        log.info("开始请求健康检查地址：{}，密钥已配置：{}", healthUrl,
                secretKey != null && !secretKey.isBlank());

        HealthCheckResult urlCheckResult = requestHealthCheckUrl(healthUrl, secretKey);
        result.setUrlHealthy(urlCheckResult.isUrlHealthy());
        result.setUrlResponseCode(urlCheckResult.getUrlResponseCode());
        result.setUrlResponseTimeMs(urlCheckResult.getUrlResponseTimeMs());
        result.setMessage(urlCheckResult.getMessage());
        result.setEndpoint(urlCheckResult.getEndpoint());

        log.info("回调任务配置健康检查完成，配置ID：{}，URL健康：{}",
            id, result.isUrlHealthy());

        return Result.success(result);
    }

    /**
     * 验证Cron表达式的合理性
     *
     * @param cronExpr Cron表达式
     * @return 验证结果
     */
    private CronValidationResult validateCronExpression(String cronExpr) {
        CronValidationResult result = new CronValidationResult();
        result.setValid(false);
        result.setDescription("");

        try {
            Cron cron = cronParser.parse(cronExpr);
            ZonedDateTime now = LocalDateTime.now().atZone(ZONE_ID);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            String description = generateCronDescription(cron);

            if (executionTime.nextExecution(now).isPresent()) {
                ZonedDateTime nextExecution = executionTime.nextExecution(now).get();
                result.setValid(true);
                result.setDescription(description);
                result.setNextExecutionTime(formatNextExecutionTime(nextExecution));
            } else {
                result.setDescription(description + "，但无法计算下次执行时间");
            }

        } catch (Exception e) {
            result.setDescription("Cron表达式格式错误：" + e.getMessage());
        }

        return result;
    }

    /**
     * 生成Cron表达式的中文含义描述（支持秒级）
     *
     * @param cron Cron对象
     * @return 中文含义描述
     */
    private String generateCronDescription(Cron cron) {
        try {
            String second = getCronFieldValue(cron, CronFieldName.SECOND);
            String minute = getCronFieldValue(cron, CronFieldName.MINUTE);
            String hour = getCronFieldValue(cron, CronFieldName.HOUR);
            String dayOfMonth = getCronFieldValue(cron, CronFieldName.DAY_OF_MONTH);
            String month = getCronFieldValue(cron, CronFieldName.MONTH);
            String dayOfWeek = getCronFieldValue(cron, CronFieldName.DAY_OF_WEEK);

            // 判断是否为“每天”的简单场景（日、月、周均为通配符）
            boolean isDaily = (isEveryField(dayOfMonth) || "?".equals(dayOfMonth)) &&
                    (isEveryField(month) || "*".equals(month)) &&
                    (isEveryField(dayOfWeek) || "?".equals(dayOfWeek));

            if (isDaily) {
                StringBuilder desc = new StringBuilder("每天");

                // 处理小时
                if (isEveryField(hour) || "*".equals(hour)) {
                    desc.append("每小时");
                } else if (hour.contains(",")) {
                    desc.append(hour.replace(",", "、")).append("点");
                } else if (hour.contains("-")) {
                    String[] parts = hour.split("-");
                    desc.append(parts[0]).append("点到").append(parts[1]).append("点");
                } else {
                    desc.append(hour).append("点");
                }

                // 处理分钟
                if (isEveryField(minute) || "*".equals(minute)) {
                    if (!isEveryField(hour) && !"*".equals(hour)) {
                        desc.append("的");
                    }
                    desc.append("每分钟");
                } else if (minute.contains(",")) {
                    desc.append("的").append(minute.replace(",", "、")).append("分");
                } else if (minute.contains("-")) {
                    String[] parts = minute.split("-");
                    desc.append("的").append(parts[0]).append("分到").append(parts[1]).append("分");
                } else {
                    if (!isEveryField(hour) && !"*".equals(hour)) {
                        desc.append("的");
                    }
                    desc.append(minute).append("分");
                }

                // 处理秒（仅当秒不是 "0" 或通配符时才显示）
                if (!isEveryField(second) && !"*".equals(second) && !"0".equals(second)) {
                    if (minute.contains("*") || isEveryField(minute)) {
                        desc.append("的");
                    } else {
                        desc.append("的");
                    }

                    if (second.contains(",")) {
                        desc.append(second.replace(",", "、")).append("秒");
                    } else if (second.contains("-")) {
                        String[] parts = second.split("-");
                        desc.append(parts[0]).append("秒到").append(parts[1]).append("秒");
                    } else {
                        desc.append(second).append("秒");
                    }
                }

                // 如果是固定时间点（非通配），使用简洁格式：每天 HH:mm:ss 执行
                if (!isEveryField(hour) && !"*".equals(hour) &&
                        !isEveryField(minute) && !"*".equals(minute) &&
                        ("0".equals(second) || (!isEveryField(second) && !"*".equals(second)))) {

                    String formattedSecond = "0".equals(second) ? "00" : second;
                    String formattedMinute = minute.length() == 1 ? "0" + minute : minute;
                    String formattedHour = hour.length() == 1 ? "0" + hour : hour;

                    return "每天 " + formattedHour + ":" + formattedMinute + ":" + formattedSecond + " 执行";
                }

                return desc.toString();
            }

            // TODO: 可扩展支持“每月”、“每周”等复杂场景（当前简化为“自定义”）
            return "自定义定时任务";

        } catch (Exception e) {
            log.warn("生成Cron中文描述失败", e);
            return "自定义定时任务";
        }
    }

    /**
     * 获取Cron字段值
     *
     * @param cron Cron对象
     * @param fieldName 字段名称
     * @return 字段值字符串
     */
    private String getCronFieldValue(Cron cron, CronFieldName fieldName) {
        try {
            Map<CronFieldName, CronField> fields = cron.retrieveFieldsAsMap();
            CronField field = fields.get(fieldName);
            return field != null ? field.getExpression().asString() : "*";
        } catch (Exception e) {
            return "*";
        }
    }

    /**
     * 判断是否是通配符字段
     *
     * @param field 字段值
     * @return 是否为通配符
     */
    private boolean isEveryField(String field) {
        return "*".equals(field) || "?".equals(field);
    }

    /**
     * 格式化下次执行时间
     *
     * @param nextExecution 下次执行时间
     * @return 格式化的时间字符串
     */
    private String formatNextExecutionTime(ZonedDateTime nextExecution) {
        return String.format("%04d-%02d-%02d %02d:%02d:%02d",
            nextExecution.getYear(),
            nextExecution.getMonthValue(),
            nextExecution.getDayOfMonth(),
            nextExecution.getHour(),
            nextExecution.getMinute(),
            nextExecution.getSecond()
        );
    }

    /**
     * 请求健康检查URL
     *
     * @param healthUrl 健康检查URL
     * @param secretKey 密钥
     * @return 健康检查结果
     */
    @SuppressWarnings("unchecked")
    private HealthCheckResult requestHealthCheckUrl(String healthUrl, String secretKey) {
        HealthCheckResult result = new HealthCheckResult();

        try {
            long startTime = System.currentTimeMillis();

            String requestId = "health-check-" + System.currentTimeMillis();

            org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
            httpHeaders.add("X-Request-Id", requestId);
            httpHeaders.add("X-Secret", secretKey);

            org.springframework.http.HttpEntity<Void> requestEntity = 
                new org.springframework.http.HttpEntity<>(httpHeaders);

            ResponseEntity<Map> response = restTemplate.exchange(
                healthUrl,
                HttpMethod.GET,
                requestEntity,
                Map.class
            );

            long responseTime = System.currentTimeMillis() - startTime;

            Map<String, Object> responseBody = response.getBody();
            
            boolean success = false;
            String endpoint = null;
            String message = null;
            
            if (responseBody != null) {
                Object codeObj = responseBody.get("code");
                boolean isSuccessCode = codeObj != null && Integer.parseInt(codeObj.toString()) == 200;
                
                Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                if (data != null) {
                    Object statusObj = data.get("status");
                    success = isSuccessCode && "UP".equals(statusObj);
                    endpoint = (String) data.get("endpoint");
                    message = (String) data.get("message");
                }
            }

            result.setUrlHealthy(success);
            result.setUrlResponseCode(response.getStatusCode().value());
            result.setUrlResponseTimeMs((int) responseTime);
            result.setEndpoint(endpoint);
            result.setMessage(message);

            log.info("健康检查请求成功，URL：{}，状态码：{}，响应时间：{}ms，健康状态：{}",
                healthUrl, response.getStatusCode(), responseTime, success ? "UP" : "DOWN");

        } catch (Exception e) {
            result.setUrlHealthy(false);
            result.setUrlResponseCode(null);
            result.setUrlResponseTimeMs(null);
            result.setMessage("请求失败：" + e.getMessage());

            log.warn("健康检查请求失败，URL：{}，错误：{}", healthUrl, e.getMessage());
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
     * Cron表达式描述结果
     */
    @Data
    public static class CronDescriptionResult {
        /** Cron表达式 */
        private String cronExpr;

        /** 中文描述 */
        private String description;

        /** 下次执行时间 */
        private String nextExecutionTime;
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

        /** URL健康状况 */
        private boolean urlHealthy;

        /** URL响应状态码 */
        private Integer urlResponseCode;

        /** URL响应时间（毫秒） */
        private Integer urlResponseTimeMs;

        /** 消息 */
        private String message;

        /** 服务端点名称 */
        private String endpoint;

        /** Cron表达式 */
        private String cronExpr;

        /** Cron表达式中文含义 */
        private String cronDescription;

        /** Cron下次执行时间 */
        private String cronNextExecutionTime;
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

        /** 下次执行时间 */
        private String nextExecutionTime;
    }
}
