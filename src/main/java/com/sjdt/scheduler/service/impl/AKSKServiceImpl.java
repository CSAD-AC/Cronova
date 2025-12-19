package com.sjdt.scheduler.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.po.AKSK;
import com.sjdt.scheduler.mapper.AKSKMapper;
import com.sjdt.scheduler.service.IAKSKService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * AKSK Service 实现类
 * 提供 AKSK 生成、验证等业务逻辑的具体实现
 */
@Service
@Slf4j
public class AKSKServiceImpl extends ServiceImpl<AKSKMapper, AKSK> implements IAKSKService {

    /**
     * 时间戳有效期（秒），用于防止重放攻击
     */
    private static final long TIMESTAMP_VALIDITY = 300;

    /**
     * 为指定 Worker 生成 AKSK
     * @param workerId Worker ID
     * @return 生成的 AKSK 实体
     */
    @Override
    public AKSK generateAKSK(String workerId) {
        log.info("开始为Worker生成AKSK，WorkerID: {}", workerId);
        
        // 生成 Access Key（使用 UUID，确保唯一性）
        String accessKey = "AK" + IdUtil.fastSimpleUUID().toUpperCase();
        // 生成 Secret Key（使用 UUID，确保安全性）
        String secretKey = "SK" + IdUtil.fastSimpleUUID().toUpperCase();

        // 创建 AKSK 实体
        AKSK aksk = AKSK.builder()
                .accessKey(accessKey)
                .secretKey(secretKey)
                .workerId(workerId)
                .status("active")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 保存到数据库
        this.save(aksk);
        log.info("AKSK生成成功，WorkerID: {}, AccessKey: {}", workerId, accessKey);

        return aksk;
    }

    /**
     * 验证 AKSK 签名
     * @param accessKey Access Key
     * @param signature 请求签名
     * @param timestamp 请求时间戳
     * @param requestBody 请求体
     * @return 验证结果，true 表示验证通过，false 表示验证失败
     */
    @Override
    public boolean validateAKSK(String accessKey, String signature, String timestamp, String requestBody) {
        log.debug("开始验证AKSK签名，AccessKey: {}", accessKey);
        
        // 1. 根据 Access Key 查询 AKSK 信息
        AKSK aksk = this.baseMapper.selectByAccessKey(accessKey);
        if (aksk == null) {
            log.warn("AKSK不存在，AccessKey: {}", accessKey);
            return false;
        }
        if (!"active".equals(aksk.getStatus())) {
            log.warn("AKSK已禁用，AccessKey: {}", accessKey);
            return false;
        }

        // 2. 验证时间戳有效性（防止重放攻击）
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            if (Math.abs(currentTime - requestTime) > TIMESTAMP_VALIDITY) {
                log.warn("AKSK签名时间戳无效，AccessKey: {}, 当前时间: {}, 请求时间: {}", 
                        accessKey, currentTime, requestTime);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("AKSK签名时间戳格式错误，AccessKey: {}", accessKey, e);
            return false;
        }

        // 3. 生成待签名字符串：timestamp + "\n" + requestBody
        String signString = timestamp + "\n" + requestBody;

        // 4. 使用 Secret Key 生成 HMAC-SHA256 签名
        HMac hMac = new HMac(HmacAlgorithm.HmacSHA256, aksk.getSecretKey().getBytes());
        String generatedSignature = hMac.digestHex(signString);

        // 5. 比较签名是否一致
        boolean isValid = generatedSignature.equals(signature);
        if (isValid) {
            log.debug("AKSK签名验证通过，AccessKey: {}", accessKey);
        } else {
            log.warn("AKSK签名验证失败，AccessKey: {}", accessKey);
        }
        return isValid;
    }

    /**
     * 根据 Access Key 查询 AKSK 信息
     * @param accessKey Access Key
     * @return AKSK 实体
     */
    @Override
    public AKSK getByAccessKey(String accessKey) {
        log.info("根据AccessKey查询AKSK信息，AccessKey: {}", accessKey);
        return this.baseMapper.selectByAccessKey(accessKey);
    }

    /**
     * 禁用指定 AKSK
     * @param id AKSK ID
     * @return 操作结果
     */
    @Override
    public boolean disableAKSK(String id) {
        log.info("开始禁用AKSK，AKSKID: {}", id);
        
        AKSK aksk = this.getById(id);
        if (aksk == null) {
            log.warn("AKSK不存在，无法禁用，AKSKID: {}", id);
            return false;
        }
        aksk.setStatus("inactive");
        aksk.setUpdateTime(LocalDateTime.now());
        boolean result = this.updateById(aksk);
        
        if (result) {
            log.info("AKSK禁用成功，AKSKID: {}", id);
        } else {
            log.error("AKSK禁用失败，AKSKID: {}", id);
        }
        return result;
    }

    /**
     * 启用指定 AKSK
     * @param id AKSK ID
     * @return 操作结果
     */
    @Override
    public boolean enableAKSK(String id) {
        log.info("开始启用AKSK，AKSKID: {}", id);
        
        AKSK aksk = this.getById(id);
        if (aksk == null) {
            log.warn("AKSK不存在，无法启用，AKSKID: {}", id);
            return false;
        }
        aksk.setStatus("active");
        aksk.setUpdateTime(LocalDateTime.now());
        boolean result = this.updateById(aksk);
        
        if (result) {
            log.info("AKSK启用成功，AKSKID: {}", id);
        } else {
            log.error("AKSK启用失败，AKSKID: {}", id);
        }
        return result;
    }

}