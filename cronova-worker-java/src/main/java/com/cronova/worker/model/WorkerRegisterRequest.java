package com.cronova.worker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Worker 注册请求（对应调度器 /api/worker/register）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerRegisterRequest {

    private String host;
    private String ip;
    private List<String> tags;
}
