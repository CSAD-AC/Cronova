package com.sjdt.scheduler.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 通用HTTP请求工具类
 * 用于发送回调请求，支持重试机制和超时控制
 */
@Slf4j
public class HttpCallbackClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HttpCallbackClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 发送回调请求
     *
     * @param url 请求URL
     * @param method HTTP方法
     * @param headers 请求头
     * @param requestBody 请求体
     * @param secretKey 密钥（用于X-Secret请求头）
     * @param timeoutSeconds 超时时间（秒）
     * @param retryCount 重试次数
     * @param retryIntervalSeconds 重试间隔（秒）
     * @return 回调执行结果
     */
    public CallbackResult executeCallback(String url, 
                                        HttpMethod method,
                                        Map<String, String> headers,
                                        Object requestBody,
                                        String secretKey,
                                        int timeoutSeconds,
                                        int retryCount,
                                        int retryIntervalSeconds) {
        
        String requestId = UUID.randomUUID().toString().replace("-", "");
        log.info("[{}] 开始发送回调请求，URL: {}, 方法: {}, 重试次数: {}", requestId, url, method, retryCount);

        int totalAttempts = retryCount + 1;
        String lastError = null;

        for (int attempt = 0; attempt < totalAttempts; attempt++) {
            try {
                CallbackResult result = executeSingleAttempt(url, method, headers, requestBody, secretKey, timeoutSeconds, requestId);
                
                if (result.isSuccess()) {
                    log.info("[{}] 回调请求成功（第{}次尝试），URL: {}, 状态码: {}, 耗时: {}ms", 
                            requestId, attempt + 1, url, result.getStatusCode(), result.getDurationMs());
                    return result;
                } else {
                    lastError = result.getErrorMessage();
                    log.warn("[{}] 回调请求失败（第{}次尝试），URL: {}, 错误: {}", 
                            requestId, attempt + 1, url, lastError);
                }
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("[{}] 回调请求异常（第{}次尝试），URL: {}, 错误: {}", 
                         requestId, attempt + 1, url, e.getMessage(), e);
            }

            // 如果不是最后一次尝试，等待重试间隔
            if (attempt < totalAttempts - 1 && retryIntervalSeconds > 0) {
                try {
                    Thread.sleep(retryIntervalSeconds * 1000L);
                    log.info("[{}] 等待重试间隔{}秒", requestId, retryIntervalSeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    lastError = "重试等待被中断";
                    break;
                }
            }
        }

        log.error("[{}] 回调请求最终失败，URL: {}, 最终错误: {}, 总尝试次数: {}", 
                 requestId, url, lastError, totalAttempts);
        return CallbackResult.fail(requestId, "最终失败: " + lastError, totalAttempts);
    }

    /**
     * 执行单次请求
     */
    private CallbackResult executeSingleAttempt(String url,
                                              HttpMethod method,
                                              Map<String, String> headers,
                                              Object requestBody,
                                              String secretKey,
                                              int timeoutSeconds,
                                              String requestId) {
        
        long startTime = System.currentTimeMillis();

        try {
            // 构建HTTP请求头
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
            
            // 设置自定义请求头
            if (headers != null) {
                headers.forEach(httpHeaders::set);
            }
            
            // 设置标准回调请求头
            httpHeaders.set("X-Request-Id", requestId);
            if (secretKey != null && !secretKey.isEmpty()) {
                httpHeaders.set("X-Secret", secretKey);
            }

            // 构建请求实体
            HttpEntity<?> requestEntity = new HttpEntity<>(requestBody, httpHeaders);

            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                url, 
                method, 
                requestEntity, 
                String.class
            );

            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode().value();

            boolean success = statusCode >= 200 && statusCode < 300;
            
            String responseBody = response.getBody();
            if (responseBody != null && responseBody.length() > 500) {
                responseBody = responseBody.substring(0, 500) + "...(truncated)";
            }

            log.info("[{}] 请求完成，状态码: {}, 耗时: {}ms, 响应体: {}", 
                    requestId, statusCode, duration, responseBody);

            return CallbackResult.success(requestId, statusCode, duration, responseBody);

        } catch (ResourceAccessException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 网络访问异常，耗时: {}ms, 错误: {}", requestId, duration, e.getMessage());
            return CallbackResult.fail(requestId, "网络访问异常: " + e.getMessage(), duration);

        } catch (RestClientException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] HTTP客户端异常，耗时: {}ms, 错误: {}", requestId, duration, e.getMessage());
            return CallbackResult.fail(requestId, "HTTP客户端异常: " + e.getMessage(), duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] 未知异常，耗时: {}ms, 错误: {}", requestId, duration, e.getMessage(), e);
            return CallbackResult.fail(requestId, "未知异常: " + e.getMessage(), duration);
        }
    }

    /**
     * 回调执行结果
     */
    public static class CallbackResult {
        private final String requestId;
        private final boolean success;
        private final int statusCode;
        private final long durationMs;
        private final String responseBody;
        private final String errorMessage;
        private final int attemptCount;

        private CallbackResult(String requestId, boolean success, int statusCode, long durationMs, 
                            String responseBody, String errorMessage, int attemptCount) {
            this.requestId = requestId;
            this.success = success;
            this.statusCode = statusCode;
            this.durationMs = durationMs;
            this.responseBody = responseBody;
            this.errorMessage = errorMessage;
            this.attemptCount = attemptCount;
        }

        public static CallbackResult success(String requestId, int statusCode, long durationMs, String responseBody) {
            return new CallbackResult(requestId, true, statusCode, durationMs, responseBody, null, 1);
        }

        public static CallbackResult fail(String requestId, String errorMessage, long durationMs) {
            return new CallbackResult(requestId, false, 0, durationMs, null, errorMessage, 1);
        }

        public static CallbackResult fail(String requestId, String errorMessage, int attemptCount) {
            return new CallbackResult(requestId, false, 0, 0, null, errorMessage, attemptCount);
        }

        // Getters
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public int getStatusCode() { return statusCode; }
        public long getDurationMs() { return durationMs; }
        public String getResponseBody() { return responseBody; }
        public String getErrorMessage() { return errorMessage; }
        public int getAttemptCount() { return attemptCount; }
    }
}
