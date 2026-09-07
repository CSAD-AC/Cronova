package com.cronova.scheduler.service;

import com.cronova.scheduler.domain.po.AKSK;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

/**
 * AKSKServiceImpl测试类
 */
@SpringBootTest
@Transactional
@Rollback
class AKSKServiceImplTest {

    @Autowired
    private IAKSKService akskService;

    @Test
    void testGenerateAKSK() {
        // 测试生成AKSK
        String workerId = "test-worker-generate";
        AKSK generatedAksk = akskService.generateAKSK(workerId);

        // 验证生成结果
        Assertions.assertNotNull(generatedAksk);
        Assertions.assertNotNull(generatedAksk.getId());
        Assertions.assertNotNull(generatedAksk.getAccessKey());
        Assertions.assertNotNull(generatedAksk.getSecretKey());
        Assertions.assertEquals(workerId, generatedAksk.getWorkerId());
        Assertions.assertEquals("active", generatedAksk.getStatus());
        Assertions.assertNotNull(generatedAksk.getCreateTime());
        Assertions.assertNotNull(generatedAksk.getUpdateTime());

        // 验证AccessKey格式
        Assertions.assertTrue(generatedAksk.getAccessKey().startsWith("AK"));
        Assertions.assertTrue(generatedAksk.getSecretKey().startsWith("SK"));
    }

    @Test
    void testValidateAKSK() {
        // 生成测试AKSK
        String workerId = "test-worker-validate";
        AKSK aksk = akskService.generateAKSK(workerId);

        // 构建测试请求
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String requestBody = "test request body";
        
        // 使用HMAC-SHA256生成签名
        cn.hutool.crypto.digest.HMac hMac = new cn.hutool.crypto.digest.HMac(
                cn.hutool.crypto.digest.HmacAlgorithm.HmacSHA256, 
                aksk.getSecretKey().getBytes());
        String signature = hMac.digestHex(timestamp);

        // 验证有效的AKSK
        boolean isValid = akskService.validateAKSK(
                aksk.getAccessKey(), 
                signature, 
                timestamp, 
                requestBody);
        Assertions.assertTrue(isValid);

        // 验证无效的AccessKey
        boolean invalidAccessKey = akskService.validateAKSK(
                "invalid-access-key", 
                signature, 
                timestamp, 
                requestBody);
        Assertions.assertFalse(invalidAccessKey);

        // 验证无效的签名
        boolean invalidSignature = akskService.validateAKSK(
                aksk.getAccessKey(), 
                "invalid-signature", 
                timestamp, 
                requestBody);
        Assertions.assertFalse(invalidSignature);
    }

    @Test
    void testGetByAccessKey() {
        // 生成测试AKSK
        String workerId = "test-worker-get-by-key";
        AKSK aksk = akskService.generateAKSK(workerId);

        // 测试根据AccessKey查询
        AKSK foundAksk = akskService.getByAccessKey(aksk.getAccessKey());

        // 验证查询结果
        Assertions.assertNotNull(foundAksk);
        Assertions.assertEquals(aksk.getId(), foundAksk.getId());
        Assertions.assertEquals(aksk.getWorkerId(), foundAksk.getWorkerId());
        Assertions.assertEquals(aksk.getStatus(), foundAksk.getStatus());

        // 测试查询不存在的AccessKey
        AKSK notFoundAksk = akskService.getByAccessKey("non-existent-key");
        Assertions.assertNull(notFoundAksk);
    }

    @Test
    void testDisableAndEnableAKSK() {
        // 生成测试AKSK
        String workerId = "test-worker-disable-enable";
        AKSK aksk = akskService.generateAKSK(workerId);

        // 测试禁用AKSK
        boolean disableResult = akskService.disableAKSK(aksk.getId());
        Assertions.assertTrue(disableResult);

        // 验证AKSK已被禁用
        AKSK disabledAksk = akskService.getByAccessKey(aksk.getAccessKey());
        Assertions.assertNotNull(disabledAksk);
        Assertions.assertEquals("inactive", disabledAksk.getStatus());

        // 测试启用AKSK
        boolean enableResult = akskService.enableAKSK(aksk.getId());
        Assertions.assertTrue(enableResult);

        // 验证AKSK已被启用
        AKSK enabledAksk = akskService.getByAccessKey(aksk.getAccessKey());
        Assertions.assertNotNull(enabledAksk);
        Assertions.assertEquals("active", enabledAksk.getStatus());
    }

    @Test
    void testDisableInvalidAKSK() {
        // 测试禁用不存在的AKSK
        boolean disableResult = akskService.disableAKSK("non-existent-id");
        Assertions.assertFalse(disableResult);
    }

    @Test
    void testEnableInvalidAKSK() {
        // 测试启用不存在的AKSK
        boolean enableResult = akskService.enableAKSK("non-existent-id");
        Assertions.assertFalse(enableResult);
    }
}