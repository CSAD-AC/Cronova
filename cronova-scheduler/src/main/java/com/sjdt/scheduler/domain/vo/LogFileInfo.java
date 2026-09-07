package com.sjdt.scheduler.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日志文件信息VO
 * 用于返回MinIO访问信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LogFileInfo", description = "日志文件信息")
public class LogFileInfo {

    @Schema(description = "MinIO存储桶名称", example = "task-logs")
    private String bucket;

    @Schema(description = "MinIO对象名称", example = "2026/01/22/task-run-id.log")
    private String objectName;

    @Schema(description = "下载时的文件名", example = "task_log_xxx.log")
    private String fileName;

    @Schema(description = "预签名访问URL（可选）", example = "https://minio.example.com/...")
    private String signedUrl;
}
