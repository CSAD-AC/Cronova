package com.sjdt.scheduler.task;

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
import java.util.*;

/**
 * 任务回调执行器
 * 负责执行任务的回调逻辑，包括发送HTTP请求和记录执行结果
 * 业务流程：
 * 1. 先插入callback_job_runs表，生成request_id
 * 2. 将完整请求数据存储到MinIO
 * 3. 携带request_id发送回调请求
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
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public TaskCallbackExecutor(CallbackJobConfigMapper callbackJobConfigMapper,
                               CallbackJobRunMapper callbackJobRunMapper,
                               MinioStorageUtil minioStorageUtil,
                               RestTemplate restTemplate,
                               ObjectMapper objectMapper) {
        this.callbackJobConfigMapper = callbackJobConfigMapper;
        this.callbackJobRunMapper = callbackJobRunMapper;
        this.minioStorageUtil = minioStorageUtil;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.httpCallbackClient = new HttpCallbackClient(restTemplate, objectMapper);
    }

    /**
     * 执行任务回调
     *
     * @param taskId 任务ID
     * @param taskRunId 任务运行实例ID
     * @param taskResult 任务执行结果
     * @param requestData 请求数据（任务参数等）
     */
    public void executeCallback(String taskId, String taskRunId, String taskResult, Map<String, Object> requestData) {
        log.info("开始执行任务回调，任务ID: {}, 任务运行ID: {}", taskId, taskRunId);

        try {
            List<CallbackJobConfig> configs = callbackJobConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                    .eq(CallbackJobConfig::getTaskId, taskId)
            );

            if (configs.isEmpty()) {
                log.info("任务没有配置回调信息，任务ID: {}", taskId);
                return;
            }

            log.info("找到{}个回调配置，开始执行回调", configs.size());

            for (CallbackJobConfig config : configs) {
                executeSingleCallback(config, taskRunId, taskResult, requestData);
            }

        } catch (Exception e) {
            log.error("执行任务回调时发生异常，任务ID: {}", taskId, e);
        }
    }

    /**
     * 执行单个回调配置
     * 业务流程：
     * 1. 创建回调执行记录（pending状态）
     * 2. 存储请求数据到MinIO
     * 3. 执行HTTP请求
     * 4. 存储响应数据到MinIO
     * 5. 更新执行记录（success/failed状态）
     *
     * @param config 回调配置
     * @param taskRunId 任务运行实例ID
     * @param taskResult 任务执行结果
     * @param requestData 请求数据
     */
    private void executeSingleCallback(CallbackJobConfig config, String taskRunId,
                                       String taskResult, Map<String, Object> requestData) {
        String configId = config.getId();
        String taskId = config.getTaskId();
        log.info("开始执行回调配置，配置ID: {}, 任务ID: {}", configId, taskId);

        CallbackJobRun runRecord = null;
        String requestId = null;
        String requestPath = null;
        long startTime = System.currentTimeMillis();

        try {
            requestId = generateRequestId();
            log.info("生成请求ID: {}", requestId);

            runRecord = createInitialRunRecord(config, taskRunId, requestId, taskResult, requestData);
            callbackJobRunMapper.insert(runRecord);
            log.info("回调执行记录已创建，记录ID: {}, 请求ID: {}", runRecord.getId(), requestId);

            Map<String, String> requestHeaders = buildRequestHeaders(requestId, config.getSecretKey());
            Map<String, Object> callbackData = buildCallbackData(config, taskRunId, taskResult, requestData);

            requestPath = minioStorageUtil.storeRequestData(requestId, requestHeaders, callbackData);
            if (requestPath != null) {
                log.info("请求数据已存储到MinIO，路径: {}", requestPath);
            }

            executeHttpCallback(config, requestId, requestHeaders, callbackData, runRecord, requestPath, startTime);

        } catch (Exception e) {
            log.error("执行回调配置时发生异常，配置ID: {}", configId, e);
            updateRunRecordOnError(runRecord, requestId, e.getMessage(), startTime);
        }
    }

    /**
     * 创建初始执行记录
     *
     * @param config 回调配置
     * @param taskRunId 任务运行实例ID
     * @param requestId 请求ID
     * @param taskResult 任务执行结果
     * @param requestData 请求数据
     * @return 初始执行记录
     */
    private CallbackJobRun createInitialRunRecord(CallbackJobConfig config, String taskRunId,
                                                   String requestId, String taskResult,
                                                   Map<String, Object> requestData) {
        LocalDateTime now = LocalDateTime.now();

        return CallbackJobRun.builder()
            .taskId(config.getTaskId())
            .configId(config.getId())
            .requestId(requestId)
            .status("pending")
            .requestUrl(config.getCallbackUrl())
            .requestMethod(config.getCallbackMethod())
            .startTime(now)
            .createTime(now)
            .build();
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
     * 构建回调请求数据
     *
     * @param config 回调配置
     * @param taskRunId 任务运行实例ID
     * @param taskResult 任务执行结果
     * @param requestData 请求数据
     * @return 回调数据Map
     */
    private Map<String, Object> buildCallbackData(CallbackJobConfig config, String taskRunId,
                                                   String taskResult, Map<String, Object> requestData) {
        Map<String, Object> callbackData = new HashMap<>();

        callbackData.put("taskId", config.getTaskId());
        callbackData.put("configId", config.getId());
        callbackData.put("taskRunId", taskRunId);
        callbackData.put("callbackUrl", config.getCallbackUrl());
        callbackData.put("callbackMethod", config.getCallbackMethod());
        callbackData.put("taskResult", taskResult);
        callbackData.put("executeTime", LocalDateTime.now().toString());

        if (requestData != null) {
            callbackData.put("requestData", requestData);
        }

        return callbackData;
    }

    /**
     * 执行HTTP回调请求
     *
     * @param config 回调配置
     * @param requestId 请求ID
     * @param requestHeaders 请求头
     * @param callbackData 回调数据
     * @param runRecord 执行记录
     * @param requestPath 请求数据MinIO路径
     * @param startTime 开始时间
     */
    private void executeHttpCallback(CallbackJobConfig config, String requestId,
                                     Map<String, String> requestHeaders,
                                     Map<String, Object> callbackData,
                                     CallbackJobRun runRecord,
                                     String requestPath, long startTime) {
        String url = config.getCallbackUrl();
        HttpMethod method = HttpMethod.valueOf(config.getCallbackMethod().toUpperCase());
        int timeoutSeconds = config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 30;
        int retryCount = config.getRetryCount() != null ? config.getRetryCount() : 0;
        int retryIntervalSeconds = config.getRetryIntervalSeconds() != null ? config.getRetryIntervalSeconds() : 60;

        updateRunRecordStatus(runRecord, "running", requestPath, null);

        HttpCallbackClient.CallbackResult result = httpCallbackClient.executeCallback(
            url, method, requestHeaders, callbackData,
            config.getSecretKey(), requestId,
            retryCount, retryIntervalSeconds
        );

        processCallbackResult(config, runRecord, requestId, requestPath, result, startTime);
    }

    /**
     * 处理回调结果
     *
     * @param config 回调配置
     * @param runRecord 执行记录
     * @param requestId 请求ID
     * @param requestPath 请求数据MinIO路径
     * @param result 回调结果
     * @param startTime 开始时间
     */
    private void processCallbackResult(CallbackJobConfig config, CallbackJobRun runRecord,
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

            updateConfigStatistics(config, result);

        } catch (Exception e) {
            log.error("处理回调结果时发生异常，请求ID: {}", requestId, e);
            updateRunRecordOnError(runRecord, requestId, e.getMessage(), startTime);
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
     * 更新执行记录（错误情况）
     *
     * @param runRecord 执行记录
     * @param requestId 请求ID
     * @param errorMessage 错误信息
     * @param startTime 开始时间
     */
    private void updateRunRecordOnError(CallbackJobRun runRecord, String requestId,
                                        String errorMessage, long startTime) {
        if (runRecord == null) return;

        try {
            runRecord.setStatus("failed");
            runRecord.setErrorMessage(errorMessage);
            runRecord.setDurationMs((int) (System.currentTimeMillis() - startTime));
            runRecord.setEndTime(LocalDateTime.now());

            callbackJobRunMapper.updateById(runRecord);

            log.error("回调执行失败，请求ID: {}, 错误: {}", requestId, errorMessage);
        } catch (Exception e) {
            log.error("更新执行记录（错误情况）失败，请求ID: {}", requestId, e);
        }
    }

    /**
     * 更新配置统计信息
     *
     * @param config 回调配置
     * @param result 回调结果
     */
    private void updateConfigStatistics(CallbackJobConfig config, HttpCallbackClient.CallbackResult result) {
        try {
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
            log.error("更新回调配置统计信息失败，配置ID: {}", config.getId(), e);
        }
    }

    /**
     * 生成请求ID
     *
     * @return 请求ID（UUID格式）
     */
    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
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

        return httpCallbackClient.executeCallback(url, method, headers, requestBody, secretKey, requestId, 0, 0);
    }

    /**
     * 获取指定任务的回调配置
     *
     * @param taskId 任务ID
     * @return 回调配置列表
     */
    public List<CallbackJobConfig> getCallbackConfigsByTaskId(String taskId) {
        return callbackJobConfigMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                .eq(CallbackJobConfig::getTaskId, taskId)
        );
    }

    /**
     * 获取指定任务的回调执行记录
     *
     * @param taskId 任务ID
     * @return 执行记录列表
     */
    public List<CallbackJobRun> getCallbackRunsByTaskId(String taskId) {
        return callbackJobRunMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobRun>()
                .eq(CallbackJobRun::getTaskId, taskId)
                .orderByDesc(CallbackJobRun::getCreateTime)
        );
    }

    /**
     * 执行测试回调（直接调用真实业务流程）
     *
     * 此方法直接执行真实的 HTTP 回调请求，与正常业务流程完全一致
     * 区别仅在于：使用测试标记的任务运行 ID，便于区分
     *
     * @param configId 回调配置ID
     * @param taskResult 任务执行结果（将发送给回调接口）
     * @return 执行记录ID
     */
    public String executeTestCallback(String configId, String taskResult) {
        log.info("【测试回调】开始执行测试回调，配置ID: {}", configId);

        CallbackJobConfig config = callbackJobConfigMapper.selectById(configId);
        if (config == null) {
            log.error("【测试回调】回调配置不存在，配置ID: {}", configId);
            throw new IllegalArgumentException("回调配置不存在: " + configId);
        }

        // 生成测试用的 taskRunId（以 TEST- 开头标识）
        String testTaskRunId = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        String requestId = "TEST-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("【测试回调】配置信息 - URL: {}, Method: {}, Secret: {}",
                config.getCallbackUrl(), config.getCallbackMethod(),
                config.getSecretKey() != null ? "已配置" : "未配置");

        // 执行真正的回调流程
        CallbackJobRun runRecord = executeRealCallback(config, testTaskRunId, requestId, taskResult);

        return runRecord != null ? runRecord.getId() : null;
    }

    /**
     * 执行真实的回调流程（与正常业务完全一致）
     */
    private CallbackJobRun executeRealCallback(CallbackJobConfig config, String taskRunId,
                                                 String requestId, String taskResult) {
        String configId = config.getId();
        String taskId = config.getTaskId();
        long startTime = System.currentTimeMillis();
        CallbackJobRun runRecord = null;

        try {
            // 1. 创建初始执行记录（pending状态）
            LocalDateTime now = LocalDateTime.now();
            runRecord = CallbackJobRun.builder()
                .taskId(taskId)
                .configId(configId)
                .requestId(requestId)
                .status("pending")
                .requestUrl(config.getCallbackUrl())
                .requestMethod(config.getCallbackMethod())
                .startTime(now)
                .createTime(now)
                .build();
            runRecord.setRetryIndex(-1); // 标记为测试回调

            callbackJobRunMapper.insert(runRecord);
            log.info("【测试回调】执行记录已创建，记录ID: {}, 请求ID: {}", runRecord.getId(), requestId);

            // 2. 构建请求头和请求数据
            Map<String, String> requestHeaders = buildRequestHeaders(requestId, config.getSecretKey());
            Map<String, Object> callbackData = buildTestCallbackData(config, taskRunId, taskResult);

            // 3. 存储请求数据到 MinIO
            String requestPath = minioStorageUtil.storeRequestData(requestId, requestHeaders, callbackData);
            if (requestPath != null) {
                log.info("【测试回调】请求数据已存储到MinIO，路径: {}", requestPath);
            }

            // 4. 更新状态为 running
            runRecord.setStatus("running");
            runRecord.setRequestPath(requestPath);
            callbackJobRunMapper.updateById(runRecord);

            // 5. 执行真正的 HTTP 请求
            log.info("【测试回调】开始发送HTTP请求，URL: {}, Method: {}", config.getCallbackUrl(), config.getCallbackMethod());

            int timeoutSeconds = config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 30;
            int retryCount = config.getRetryCount() != null ? config.getRetryCount() : 0;
            int retryIntervalSeconds = config.getRetryIntervalSeconds() != null ? config.getRetryIntervalSeconds() : 60;

            HttpCallbackClient.CallbackResult result = httpCallbackClient.executeCallback(
                config.getCallbackUrl(),
                HttpMethod.valueOf(config.getCallbackMethod().toUpperCase()),
                requestHeaders,
                callbackData,
                config.getSecretKey(),
                requestId,
                retryCount,
                retryIntervalSeconds
            );

            // 6. 存储响应数据到 MinIO
            String responsePath = storeResponseData(requestId, result);

            // 7. 更新执行记录
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

            log.info("【测试回调】回调执行完成，请求ID: {}, 状态: {}, 状态码: {}, 耗时: {}ms",
                requestId, runRecord.getStatus(), result.getStatusCode(), durationMs);

            // 8. 更新配置统计
            updateConfigStatistics(config, result);

            return runRecord;

        } catch (Exception e) {
            log.error("【测试回调】执行回调时发生异常，配置ID: {}", configId, e);
            if (runRecord != null) {
                runRecord.setStatus("failed");
                runRecord.setErrorMessage(e.getMessage());
                runRecord.setDurationMs((int) (System.currentTimeMillis() - startTime));
                runRecord.setEndTime(LocalDateTime.now());
                callbackJobRunMapper.updateById(runRecord);
            }
            return runRecord;
        }
    }

    /**
     * 构建测试回调请求数据
     */
    private Map<String, Object> buildTestCallbackData(CallbackJobConfig config, String taskRunId, String taskResult) {
        Map<String, Object> callbackData = new HashMap<>();

        callbackData.put("taskId", config.getTaskId());
        callbackData.put("configId", config.getId());
        callbackData.put("taskRunId", taskRunId);
        callbackData.put("callbackUrl", config.getCallbackUrl());
        callbackData.put("callbackMethod", config.getCallbackMethod());
        callbackData.put("taskResult", taskResult);
        callbackData.put("executeTime", LocalDateTime.now().toString());

        // 测试标记
        callbackData.put("_test", true);
        callbackData.put("_testTime", LocalDateTime.now().toString());
        callbackData.put("_source", "callback-test-api");

        return callbackData;
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
            requestId = generateRequestId();
            log.info("生成请求ID: {}", requestId);

            runRecord = createCallbackRunRecord(message, requestId);
            callbackJobRunMapper.insert(runRecord);
            log.info("回调执行记录已创建，记录ID: {}, 请求ID: {}", runRecord.getId(), requestId);

            Map<String, String> requestHeaders = buildRequestHeaders(requestId, message.getSecretKey());
            Map<String, Object> callbackData = buildCallbackJobData(message);

            requestPath = minioStorageUtil.storeRequestData(requestId, requestHeaders, callbackData);
            if (requestPath != null) {
                log.info("请求数据已存储到MinIO，路径: {}", requestPath);
            }

            executeCallbackHttpRequest(message, requestId, requestHeaders, callbackData, runRecord, requestPath, startTime);

        } catch (Exception e) {
            log.error("执行HTTP回调任务时发生异常，任务ID: {}, 配置ID: {}", taskId, configId, e);
            updateCallbackRunRecordOnError(runRecord, requestId, e.getMessage(), startTime);
        }
    }

    /**
     * 创建回调任务的执行记录
     *
     * @param message 回调任务执行消息
     * @param requestId 请求ID
     * @return 执行记录
     */
    private CallbackJobRun createCallbackRunRecord(CallbackJobExecuteMessage message, String requestId) {
        LocalDateTime now = LocalDateTime.now();

        return CallbackJobRun.builder()
            .taskId(message.getTaskId())
            .configId(message.getConfigId())
            .requestId(requestId)
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

        HttpCallbackClient.CallbackResult result = httpCallbackClient.executeCallback(
            url, method, requestHeaders, callbackData,
            message.getSecretKey(), requestId,
            retryCount, retryIntervalSeconds
        );

        processCallbackJobResult(message, runRecord, requestId, requestPath, result, startTime);
    }

    /**
     * 处理回调任务结果
     *
     * @param message 回调任务执行消息
     * @param runRecord 执行记录
     * @param requestId 请求ID
     * @param requestPath 请求数据MinIO路径
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
            log.error("处理回调任务结果时发生异常，请求ID: {}", requestId, e);
            updateCallbackRunRecordOnError(runRecord, requestId, e.getMessage(), startTime);
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
