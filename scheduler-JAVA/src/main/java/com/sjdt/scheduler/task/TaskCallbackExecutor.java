package com.sjdt.scheduler.task;

import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.CallbackJobRun;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.CallbackJobRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务回调执行器
 * 负责执行任务的回调逻辑，包括发送HTTP请求和记录执行结果
 */
@Slf4j
@Component
public class TaskCallbackExecutor {

    private final HttpCallbackClient httpCallbackClient;
    private final CallbackJobConfigMapper callbackJobConfigMapper;
    private final CallbackJobRunMapper callbackJobRunMapper;

    @Autowired
    public TaskCallbackExecutor(CallbackJobConfigMapper callbackJobConfigMapper, 
                               CallbackJobRunMapper callbackJobRunMapper) {
        this.httpCallbackClient = new HttpCallbackClient();
        this.callbackJobConfigMapper = callbackJobConfigMapper;
        this.callbackJobRunMapper = callbackJobRunMapper;
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
            // 查询任务的所有回调配置
            List<CallbackJobConfig> configs = callbackJobConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                    .eq(CallbackJobConfig::getTaskId, taskId)
            );

            if (configs.isEmpty()) {
                log.info("任务没有配置回调信息，任务ID: {}", taskId);
                return;
            }

            log.info("找到{}个回调配置，开始执行回调", configs.size());

            // 为每个回调配置执行回调
            for (CallbackJobConfig config : configs) {
                executeSingleCallback(config, taskRunId, taskResult, requestData);
            }

        } catch (Exception e) {
            log.error("执行任务回调时发生异常，任务ID: {}", taskId, e);
        }
    }

    /**
     * 执行单个回调配置
     */
    private void executeSingleCallback(CallbackJobConfig config, String taskRunId, String taskResult, Map<String, Object> requestData) {
        String configId = config.getId();
        log.info("开始执行回调配置，配置ID: {}", configId);

        try {
            // 构建请求数据
            Map<String, Object> callbackData = buildCallbackData(config, taskRunId, taskResult, requestData);

            // 执行HTTP请求
            HttpCallbackClient.CallbackResult result = httpCallbackClient.executeCallback(
                config.getCallbackUrl(),
                HttpMethod.valueOf(config.getCallbackMethod().toUpperCase()),
                null, // 可以在这里添加额外的请求头
                callbackData,
                config.getSecretKey(),
                config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 30,
                config.getRetryCount() != null ? config.getRetryCount() : 0,
                config.getRetryIntervalSeconds() != null ? config.getRetryIntervalSeconds() : 60
            );

            // 记录执行结果
            recordCallbackExecution(config, taskRunId, result, callbackData);

            // 更新配置统计信息
            updateConfigStatistics(config, result);

        } catch (Exception e) {
            log.error("执行回调配置时发生异常，配置ID: {}", configId, e);
        }
    }

    /**
     * 构建回调请求数据
     */
    private Map<String, Object> buildCallbackData(CallbackJobConfig config, String taskRunId, String taskResult, Map<String, Object> requestData) {
        Map<String, Object> callbackData = new java.util.HashMap<>();
        
        // 基本信息
        callbackData.put("taskId", config.getTaskId());
        callbackData.put("configId", config.getId());
        callbackData.put("taskRunId", taskRunId);
        callbackData.put("callbackUrl", config.getCallbackUrl());
        callbackData.put("callbackMethod", config.getCallbackMethod());
        
        // 任务执行结果
        callbackData.put("taskResult", taskResult);
        callbackData.put("executeTime", LocalDateTime.now().toString());
        
        // 原始请求数据
        if (requestData != null) {
            callbackData.put("requestData", requestData);
        }

        return callbackData;
    }

    /**
     * 记录回调执行结果
     */
    private void recordCallbackExecution(CallbackJobConfig config, String taskRunId, 
                                      HttpCallbackClient.CallbackResult result, Map<String, Object> requestData) {
        
        CallbackJobRun run = CallbackJobRun.builder()
            .taskId(config.getTaskId())
            .configId(config.getId())
            .taskRunId(taskRunId)
            .requestId(result.getRequestId())
            .status(result.isSuccess() ? "success" : "failed")
            .requestUrl(config.getCallbackUrl())
            .requestMethod(config.getCallbackMethod())
            .responseStatusCode(result.getStatusCode())
            .responseTimeMs((int) result.getDurationMs())
            .retryIndex(0) // 暂时设置为0，后续可以扩展支持多次重试记录
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now())
            .durationMs((int) result.getDurationMs())
            .errorMessage(result.isSuccess() ? null : result.getErrorMessage())
            .createTime(LocalDateTime.now())
            .build();

        try {
            callbackJobRunMapper.insert(run);
            log.info("回调执行记录已保存，记录ID: {}, 请求ID: {}", run.getId(), result.getRequestId());
        } catch (Exception e) {
            log.error("保存回调执行记录失败，配置ID: {}, 请求ID: {}", config.getId(), result.getRequestId(), e);
        }
    }

    /**
     * 更新配置统计信息
     */
    private void updateConfigStatistics(CallbackJobConfig config, HttpCallbackClient.CallbackResult result) {
        try {
            // 获取当前统计数据
            Integer totalCount = config.getTotalCallbackCount() != null ? config.getTotalCallbackCount() : 0;
            Integer successCount = config.getSuccessCount() != null ? config.getSuccessCount() : 0;
            Integer failedCount = config.getFailedCount() != null ? config.getFailedCount() : 0;

            // 更新统计
            totalCount++;
            if (result.isSuccess()) {
                successCount++;
            } else {
                failedCount++;
            }

            // 更新配置
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
     * 执行单次HTTP请求（用于测试）
     */
    public HttpCallbackClient.CallbackResult executeSingleRequest(String url, HttpMethod method, 
                                                                String secretKey, Object requestBody) {
        return httpCallbackClient.executeCallback(url, method, null, requestBody, secretKey, 30, 0, 0);
    }

    /**
     * 获取指定任务的回调配置
     */
    public List<CallbackJobConfig> getCallbackConfigsByTaskId(String taskId) {
        return callbackJobConfigMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobConfig>()
                .eq(CallbackJobConfig::getTaskId, taskId)
        );
    }

    /**
     * 获取指定任务的回调执行记录
     */
    public List<CallbackJobRun> getCallbackRunsByTaskId(String taskId) {
        return callbackJobRunMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CallbackJobRun>()
                .eq(CallbackJobRun::getTaskId, taskId)
                .orderByDesc(CallbackJobRun::getCreateTime)
        );
    }
}
