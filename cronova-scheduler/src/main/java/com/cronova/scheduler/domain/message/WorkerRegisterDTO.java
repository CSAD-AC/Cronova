package com.cronova.scheduler.domain.message;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Set;

/**
 * Worker 注册请求 DTO
 */
@Data
@Schema(description = "Worker注册请求DTO")
public class WorkerRegisterDTO {

    @Schema(description = "主机名", example = "worker-01")
    private String host;

    @Schema(description = "内网IP地址", example = "192.168.1.100")
    private String ip;

    @Schema(description = "标签列表（Docker镜像标签，如: V1, V2, FastAPI, Python3.9）", example = "[\"V1\", \"FastAPI\"]")
    private Set<String> tags;

}
