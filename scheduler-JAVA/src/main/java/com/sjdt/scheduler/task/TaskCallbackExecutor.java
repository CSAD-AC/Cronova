package com.sjdt.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.domain.message.CallbackJobExecuteMessage;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.CallbackJobRun;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.CallbackJobRunMapper;
import com.sjdt.scheduler.util.MinioStorageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 任务回调执行器
 * 负责执行HTTP回调任务，由调度器定时检测回调配置后发起调用
 * 业务流程：
 * 1. 创建回调执行记录（pending状态），request_id 由数据库自动生成
 * 2. 将完整请求数据存储到MinIO
 * 3. 发送HTTP回调请求
 * 4. 将完整响应数据存储到MinIO
 * 5. 根据执行结果更新数据库记录
 */
@Slf4j
@Component
public class TaskCallbackExecutor {

    private final HttpCallbackClient httpCallbackClient;
    private final CallbackJobConfigMapper callbackJobConfigMapper;
    private final CallbackJobRunMapper callbackJobRunMapper;
    private final MinioStorageUtil minioStorageUtil;

    @Autowired
    public TaskCallbackExecutor(CallbackJobConfigMapper callbackJobConfigMapper,
                                CallbackJobRunMapper callbackJobRunMapper,
                                MinioStorageUtil minioStorageUtil,
                                RestTemplate restTemplate,
                                ObjectMapper objectMapper) {
        this.callbackJobConfigMapper = callbackJobConfigMapper;
        this.callbackJobRunMapper = callbackJobRunMapper;
        this.minioStorageUtil = minioStorageUtil;
        this.httpCallbackClient = new HttpCallbackClient(restTemplate, objectMapper);
    }

    /**
     * 执行HTTP回调任务
     * 由调度器调用，执行配置的HTTP回调请求
     *
     * @param message 回调任务执行消息
     */
    public void executeCallbackJob(CallbackJobExecuteMessage message) {
        log.info("开始执行HTTP回调任务，任务ID: {}, 配置ID: {}, URL: {}",
            message.getTaskId(), message.getConfigId(), message.getCallbackUrl());

        String taskId = message.getTaskId();
        String taskRunId = message.getTaskRunId();
        String configId = message.getConfigId();

        CallbackJobRun runRecord = null;
        String requestId = null;
        String requestPath = null;
        long startTime = System.currentTimeMillis();

        try {
            runRecord = createCallbackRunRecord(message);
            callbackJobRunMapper.insert(runRecord);
            log.info("回调执行记录已创建，记录ID: {}, 请求ID: {}", runRecord.getId(), runRecord.getRequestId());

            requestId = runRecord.getRequestId();
            log.info("从数据库获取自动生成的请求ID: {}", requestId);

            // 如果requestId为null，则生成一个新的ID
            if (requestId == null || requestId.trim().isEmpty()) {
                requestId = generateRequestId();
                // 更新runRecord中的requestId并保存到数据库
                runRecord.setRequestId(requestId);
                
                // 使用updateById更新整个对象，这样会更新所有非空字段
                callbackJobRunMapper.updateById(runRecord);
                
                log.info("已更新数据库中的请求ID: {}", requestId);
            }

            Map<String, String> requestHeaders = buildRequestHeaders(requestId, message.getSecretKey());
            Map<String, Object> callbackData = buildCallbackJobData(message);

            requestPath = minioStorageUtil.storeRequestData(requestId, requestHeaders, callbackData);

            executeCallbackHttpRequest(message, requestId, requestHeaders, callbackData, runRecord, requestPath, startTime);

        } catch (Exception e) {
            log.error("执行HTTP回调任务时发生异常，任务ID: {}, 配置ID: {}", taskId, configId, e);
            updateCallbackRunRecordOnError(runRecord, requestId, e.getMessage(), startTime);
        }
    }

    /**
     * 执行单次HTTP请求（用于重试）
     *
     * @param url 请求URL
     * @param method HTTP方法
     * @param secretKey 密钥
     * @param requestBody 请求体
     * @return 回调结果
     */
    public HttpCallbackClient.CallbackResult executeSingleRequest(String url, HttpMethod method,
                                                                  String secretKey, Object requestBody) {
        String requestId = generateRequestId();
        Map<String, String> headers = buildRequestHeaders(requestId, secretKey);

        log.info("执行HTTP请求，URL: {}, Method: {}, RequestID: {}", url, method, requestId);

        HttpCallbackClient.CallbackResult result = httpCallbackClient.executeCallback(
            url, method, headers, requestBody, secretKey, requestId, 0, 0);

        log.info("HTTP请求完成，RequestID: {}, StatusCode: {}, Success: {}, Duration: {}ms",
            requestId, result.getStatusCode(), result.isSuccess(), result.getDurationMs());

        return result;
    }

    /**
     * 生成请求ID（仅用于重试等独立请求场景，回调主流程由数据库自动生成）
     *
     * @return 请求ID（UUID格式）
     */
    private String generateRequestId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建请求头
     *
     * @param requestId 请求ID
     * @param secretKey 密钥
     * @return 请求头Map
     */
    private Map<String, String> buildRequestHeaders(String requestId, String secretKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Request-Id", requestId);
        if (secretKey != null && !secretKey.isEmpty()) {
            headers.put("X-Secret", secretKey);
        }
        return headers;
    }

    /**
     * 创建回调任务的执行记录
     * request_id 由数据库自动生成（DEFAULT UUID()）
     *
     * @param message 回调任务执行消息
     * @return 执行记录
     */
    private CallbackJobRun createCallbackRunRecord(CallbackJobExecuteMessage message) {
        LocalDateTime now = LocalDateTime.now();

        return CallbackJobRun.builder()
            .taskId(message.getTaskId())
            .configId(message.getConfigId())
            .requestId(null)  // request_id 由数据库自动生成
            .status("pending")
            .requestUrl(message.getCallbackUrl())
            .requestMethod(message.getCallbackMethod())
            .startTime(now)
            .createTime(now)
            .build();
    }

    /**
     * 构建回调任务的请求数据
     *
     * @param message 回调任务执行消息
     * @return 回调数据Map
     */
    private Map<String, Object> buildCallbackJobData(CallbackJobExecuteMessage message) {
        Map<String, Object> callbackData = new HashMap<>();

        callbackData.put("taskId", message.getTaskId());
        callbackData.put("configId", message.getConfigId());
        callbackData.put("taskRunId", message.getTaskRunId());
        callbackData.put("callbackUrl", message.getCallbackUrl());
        callbackData.put("callbackMethod", message.getCallbackMethod());
        callbackData.put("taskResult", message.getTaskResult());
        callbackData.put("executeTime", LocalDateTime.now().toString());

        if (message.getRequestData() != null) {
            callbackData.put("requestData", message.getRequestData());
        }

        return callbackData;
    }

    /**
     * 执行回调任务的HTTP请求
     *
     * @param message 回调任务执行消息
     * @param requestId 请求ID
     * @param requestHeaders 请求头
     * @param callbackData 回调数据
     * @param runRecord 执行记录
     * @param requestPath 请求数据MinIO路径
     * @param startTime 开始时间
     */
    private void executeCallbackHttpRequest(CallbackJobExecuteMessage message, String requestId,
                                             Map<String, String> requestHeaders,
                                             Map<String, Object> callbackData,
                                             CallbackJobRun runRecord,
                                             String requestPath, long startTime) {
        String url = message.getCallbackUrl();
        HttpMethod method = HttpMethod.valueOf(message.getCallbackMethod().toUpperCase());
        int timeoutSeconds = message.getTimeoutSeconds() != null ? message.getTimeoutSeconds() : 30;
        int retryCount = message.getRetryCount() != null ? message.getRetryCount() : 0;
        int retryIntervalSeconds = message.getRetryIntervalSeconds() != null ? message.getRetryIntervalSeconds() : 60;

        updateRunRecordStatus(runRecord, "running", requestPath, null);

        log.info("发送HTTP回调请求，RequestID: {}, URL: {}, Method: {}, RetryCount: {}",
            requestId, url, method, retryCount);

        HttpCallbackClient.CallbackResult result = httpCallbackClient.executeCallback(
            url, method, requestHeaders, callbackData,
            message.getSecretKey(), requestId,
            retryCount, retryIntervalSeconds
        );

        log.info("HTTP回调请求完成，RequestID: {}, StatusCode: {}, Success: {}, Duration: {}ms",
            requestId, result.getStatusCode(), result.isSuccess(), result.getDurationMs());

        processCallbackJobResult(message, runRecord, requestId, requestPath, result, startTime);
    }

    /**
     * 处理回调任务结果
     *
     * @param message 回调任务执行消息
     * @param runRecord 执行记录
     * @param requestId 请求ID
     * @param requestPath 请求数据路径
     * @param result 回调结果
     * @param startTime 开始时间
     */
    private void processCallbackJobResult(CallbackJobExecuteMessage message, CallbackJobRun runRecord,
                                           String requestId, String requestPath,
                                           HttpCallbackClient.CallbackResult result, long startTime) {
        try {
            String responsePath = storeResponseData(requestId, result);

            int durationMs = (int) (System.currentTimeMillis() - startTime);

            runRecord.setStatus(result.isSuccess() ? "success" : "failed");
            runRecord.setResponseStatusCode(result.getStatusCode());
            runRecord.setResponseTimeMs((int) result.getDurationMs());
            runRecord.setRequestPath(requestPath);
            runRecord.setResponsePath(responsePath);
            runRecord.setDurationMs(durationMs);
            runRecord.setEndTime(LocalDateTime.now());
            runRecord.setErrorMessage(result.isSuccess() ? null : result.getErrorMessage());

            callbackJobRunMapper.updateById(runRecord);

            log.info("回调执行记录已更新，请求ID: {}, 状态: {}, 状态码: {}, 耗时: {}ms",
                requestId, runRecord.getStatus(), result.getStatusCode(), durationMs);

            updateCallbackConfigStatistics(message, result);

        } catch (Exception e) {
            log.error("处理回调结果时发生异常，请求ID: {}", requestId, e);
            updateCallbackRunRecordOnError(runRecord, requestId, e.getMessage(), startTime);
        }
    }

    /**
     * 存储响应数据到MinIO
     *
     * @param requestId 请求ID
     * @param result 回调结果
     * @return MinIO存储路径
     */
    private String storeResponseData(String requestId, HttpCallbackClient.CallbackResult result) {
        try {
            Map<String, String> responseHeaders = new HashMap<>();
            responseHeaders.put("X-Request-Id", requestId);

            return minioStorageUtil.storeResponseData(
                requestId,
                result.getStatusCode(),
                responseHeaders,
                result.getResponseBody()
            );
        } catch (Exception e) {
            log.error("存储响应数据到MinIO失败，请求ID: {}", requestId, e);
            return null;
        }
    }

    /**
     * 更新执行记录状态
     *
     * @param runRecord 执行记录
     * @param status 状态
     * @param requestPath 请求数据路径
     * @param responsePath 响应数据路径
     */
    private void updateRunRecordStatus(CallbackJobRun runRecord, String status,
                                       String requestPath, String responsePath) {
        if (runRecord == null) return;

        runRecord.setStatus(status);
        runRecord.setRequestPath(requestPath);
        runRecord.setResponsePath(responsePath);

        try {
            callbackJobRunMapper.updateById(runRecord);
        } catch (Exception e) {
            log.error("更新执行记录状态失败，记录ID: {}", runRecord.getId(), e);
        }
    }

    /**
     * 更新回调配置统计信息
     *
     * @param message 回调任务执行消息
     * @param result 回调结果
     */
    private void updateCallbackConfigStatistics(CallbackJobExecuteMessage message, HttpCallbackClient.CallbackResult result) {
        try {
            CallbackJobConfig config = callbackJobConfigMapper.selectById(message.getConfigId());
            if (config == null) {
                log.warn("回调配置不存在，配置ID: {}", message.getConfigId());
                return;
            }

            Integer totalCount = config.getTotalCallbackCount() != null ? config.getTotalCallbackCount() : 0;
            Integer successCount = config.getSuccessCount() != null ? config.getSuccessCount() : 0;
            Integer failedCount = config.getFailedCount() != null ? config.getFailedCount() : 0;

            totalCount++;
            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }

            config.setTotalCallbackCount(totalCount);
            config.setSuccessCount(successCount);
            config.setFailedCount(failedCount);
            config.setLastCallbackTime(LocalDateTime.now());
            config.setLastCallbackStatus(result.isSuccess() ? "success" : "failed");
            config.setUpdateTime(LocalDateTime.now());

            callbackJobConfigMapper.updateById(config);

            log.info("回调配置统计已更新，配置ID: {}, 总次数: {}, 成功: {}, 失败: {}",
                config.getId(), totalCount, successCount, failedCount);

        } catch (Exception e) {
            log.error("更新回调配置统计信息失败，配置ID: {}", message.getConfigId(), e);
        }
    }

    /**
     * 更新回调任务执行记录（错误情况）
     *
     * @param runRecord 执行记录
     * @param requestId 请求ID
     * @param errorMessage 错误信息
     * @param startTime 开始时间
     */
    private void updateCallbackRunRecordOnError(CallbackJobRun runRecord, String requestId,
                                                  String errorMessage, long startTime) {
        if (runRecord == null) return;

        try {
            runRecord.setStatus("failed");
            runRecord.setErrorMessage(errorMessage);
            runRecord.setDurationMs((int) (System.currentTimeMillis() - startTime));
            runRecord.setEndTime(LocalDateTime.now());

            callbackJobRunMapper.updateById(runRecord);

            log.error("HTTP回调任务执行失败，请求ID: {}, 错误: {}", requestId, errorMessage);
        } catch (Exception e) {
            log.error("更新执行记录（错误情况）失败，请求ID: {}", requestId, e);
        }
    }
}
