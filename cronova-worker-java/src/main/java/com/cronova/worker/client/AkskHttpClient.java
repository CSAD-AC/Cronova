package com.cronova.worker.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cronova.worker.common.WorkerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * 调度器 HTTP 客户端（AKSK 签名认证）
 * <p>
 * 签名规则与调度器 AKSKInterceptor / AKSKServiceImpl 对齐：
 * X-Signature = HMAC-SHA256(SK, timestamp)，timestamp 为秒级字符串。
 * 注册与 AKSK 验证请求不加签名头。
 */
@Slf4j
@Component
public class AkskHttpClient {

    private static final String HEADER_ACCESS_KEY = "X-Access-Key";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";

    private final WorkerContext workerContext;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AkskHttpClient(WorkerContext workerContext, ObjectMapper objectMapper) {
        this.workerContext = workerContext;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * POST 请求（带 AKSK 签名头）
     */
    public ApiResponse post(String url, Object body, Map<String, String> queryParams) {
        return doPost(url, body, queryParams, true);
    }

    /**
     * POST 请求（不带签名头，用于注册与 AKSK 验证）
     */
    public ApiResponse postUnsigned(String url, Object body, Map<String, String> queryParams) {
        return doPost(url, body, queryParams, false);
    }

    private ApiResponse doPost(String url, Object body, Map<String, String> queryParams, boolean signed) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(buildUrl(url, queryParams)))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json;charset=utf-8");

            if (signed) {
                String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
                String signature = sign(timestamp);
                builder.header(HEADER_ACCESS_KEY, workerContext.getAccessKey())
                        .header(HEADER_TIMESTAMP, timestamp)
                        .header(HEADER_SIGNATURE, signature);
            }

            byte[] payload = body == null ? "{}".getBytes(StandardCharsets.UTF_8)
                    : objectMapper.writeValueAsBytes(body);
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(payload));

            HttpResponse<String> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String responseBody = response.body() == null ? "" : response.body();
            if (response.statusCode() >= 400) {
                log.warn("HTTP 请求失败，status={}, url={}, body={}", response.statusCode(), url,
                        responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
                return ApiResponse.error(response.statusCode(), "HTTP " + response.statusCode());
            }
            try {
                JsonNode node = objectMapper.readTree(responseBody);
                return new ApiResponse(node.path("code").asInt(-1),
                        node.path("message").asText(""), node.path("data"));
            } catch (Exception e) {
                log.warn("HTTP 响应解析失败，url={}, body={}", url,
                        responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody);
                return ApiResponse.error(-1, "响应解析失败: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("HTTP 请求异常，url={}, err={}", url, e.getMessage());
            return ApiResponse.error(-1, e.getMessage());
        }
    }

    private String buildUrl(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        boolean first = !url.contains("?");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            sb.append(first ? '?' : '&');
            first = false;
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    /** HMAC-SHA256(SK, timestamp) 十六进制小写 */
    private String sign(String timestamp) {
        return hmacSha256(workerContext.getSecretKey(), timestamp);
    }

    /** HMAC-SHA256 十六进制小写（与调度器 AKSKServiceImpl 的 Hutool digestHex 一致） */
    public static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("生成签名失败", e);
        }
    }

    /** 通用 API 响应结构（对应调度器 Result） */
    public record ApiResponse(int code, String message, JsonNode data) {
        public boolean isOk() {
            return code == 200;
        }

        public static ApiResponse error(int code, String message) {
            return new ApiResponse(code, message, null);
        }
    }
}
