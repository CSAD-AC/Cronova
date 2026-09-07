package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cronova.scheduler.domain.po.AKSK;

/**
 * AKSK Service 接口
 * 提供 AKSK 生成、验证等业务逻辑
 */
public interface IAKSKService extends IService<AKSK> {

    /**
     * 为指定 Worker 生成 AKSK
     * @param workerId Worker ID
     * @return 生成的 AKSK 实体
     */
    AKSK generateAKSK(String workerId);

    /**
     * 验证 AKSK 签名
     * @param accessKey Access Key
     * @param signature 请求签名
     * @param timestamp 请求时间戳
     * @param requestBody 请求体
     * @return 验证结果，true 表示验证通过，false 表示验证失败
     */
    boolean validateAKSK(String accessKey, String signature, String timestamp, String requestBody);

    /**
     * 根据 Access Key 查询 AKSK 信息
     * @param accessKey Access Key
     * @return AKSK 实体
     */
    AKSK getByAccessKey(String accessKey);

    /**
     * 禁用指定 AKSK
     * @param id AKSK ID
     * @return 操作结果
     */
    boolean disableAKSK(String id);

    /**
     * 启用指定 AKSK
     * @param id AKSK ID
     * @return 操作结果
     */
    boolean enableAKSK(String id);

}