package com.sjdt.scheduler.domain.dto;

import lombok.Data;

/**
 * Worker 更新请求 DTO
 * 用于更新 Worker 的主机名
 */
@Data
public class WorkerUpdateDTO {

    /** 主机名 */
    private String host;

}