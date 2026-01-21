package com.sjdt.scheduler.util;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * MinIO存储服务工具类
 * 用于存储回调请求/响应的完整数据到MinIO对象存储
 */
@Slf4j
@Component
public class MinioStorageUtil {

    private static final String CALLBACK_DATA_FOLDER = "callback/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String BUCKET_NAME;

    /**
     * 存储回调请求数据
     *
     * @param requestId 请求ID
     * @param requestHeaders 请求头信息
     * @param requestBody 请求体内容
     * @return MinIO存储路径
     */
    public String storeRequestData(String requestId, Map<String, String> requestHeaders, Object requestBody) {
        try {
            ensureBucketExists();

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("requestId", requestId);
            dataMap.put("timestamp", LocalDateTime.now().toString());
            dataMap.put("headers", requestHeaders);
            dataMap.put("body", requestBody);

            String objectName = generateObjectName(requestId, "request");
            String jsonContent = JacksonHolder.OBJECT_MAPPER.writeValueAsString(dataMap);

            uploadToMinio(objectName, jsonContent);

            log.info("请求数据已存储到MinIO，路径: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("存储请求数据到MinIO失败，requestId: {}", requestId, e);
            return null;
        }
    }

    /**
     * 存储回调响应数据
     *
     * @param requestId 请求ID（用于关联请求）
     * @param statusCode 响应状态码
     * @param responseHeaders 响应头信息
     * @param responseBody 响应体内容
     * @return MinIO存储路径
     */
    public String storeResponseData(String requestId, int statusCode, Map<String, String> responseHeaders, String responseBody) {
        try {
            ensureBucketExists();

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("requestId", requestId);
            dataMap.put("timestamp", LocalDateTime.now().toString());
            dataMap.put("statusCode", statusCode);
            dataMap.put("headers", responseHeaders);
            dataMap.put("body", responseBody);

            String objectName = generateObjectName(requestId, "response");
            String jsonContent = JacksonHolder.OBJECT_MAPPER.writeValueAsString(dataMap);

            uploadToMinio(objectName, jsonContent);

            log.info("响应数据已存储到MinIO，路径: {}", objectName);
            return objectName;

        } catch (Exception e) {
            log.error("存储响应数据到MinIO失败，requestId: {}", requestId, e);
            return null;
        }
    }

    /**
     * 生成MinIO对象存储路径
     *
     * @param requestId 请求ID
     * @param dataType 数据类型（request/response）
     * @return 对象存储路径
     */
    private String generateObjectName(String requestId, String dataType) {
        String dateFolder = LocalDate.now().format(DATE_FORMATTER);
        return CALLBACK_DATA_FOLDER + dateFolder + "/" + requestId + "-" + dataType + ".json";
    }

    /**
     * 确保存储桶存在，不存在则创建
     */
    private void ensureBucketExists() {
        try {
            boolean found = minioClient.bucketExists(
                io.minio.BucketExistsArgs.builder().bucket(BUCKET_NAME).build()
            );
            if (!found) {
                minioClient.makeBucket(
                    io.minio.MakeBucketArgs.builder().bucket(BUCKET_NAME).build()
                );
                log.info("创建MinIO存储桶: {}", BUCKET_NAME);
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException |
                 InvalidKeyException | InvalidResponseException | IOException | NoSuchAlgorithmException |
                 ServerException | XmlParserException e) {
            log.error("检查/创建MinIO存储桶失败: {}", e.getMessage(), e);
            throw new RuntimeException("MinIO存储桶操作失败", e);
        }
    }

    /**
     * 上传数据到MinIO
     *
     * @param objectName 对象名称
     * @param content 内容字符串
     */
    private void uploadToMinio(String objectName, String content) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)
            );

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(BUCKET_NAME)
                    .object(objectName)
                    .stream(inputStream, content.length(), -1)
                    .contentType("application/json")
                    .build()
            );
        } catch (ServerException e) {
            throw new RuntimeException(e);
        } catch (InsufficientDataException e) {
            throw new RuntimeException(e);
        } catch (ErrorResponseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (InvalidResponseException e) {
            throw new RuntimeException(e);
        } catch (XmlParserException e) {
            throw new RuntimeException(e);
        } catch (InternalException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Jackson对象持有者
     */
    private static class JacksonHolder {
        private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER;

        static {
            OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
            OBJECT_MAPPER.findAndRegisterModules();
        }
    }
}
