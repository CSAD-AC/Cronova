package com.sjdt.scheduler.domain.vo;

import lombok.Data;

/**
 * Worker 注册响应 VO
 */
@Data
public class WorkerRegisterVO {

    /** Worker ID */
    private String workerId;

    /** Access Key */
    private String accessKey;

    /** Secret Key */
    private String secretKey;

}