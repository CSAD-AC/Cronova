package com.cronova.scheduler.service;

import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.po.Script;
import com.cronova.scheduler.domain.vo.ScriptVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

/**
 * ScriptService测试类
 */
@SpringBootTest
@Transactional
@Rollback
class ScriptServiceImplTest {

    @Autowired
    private IScriptService scriptService;

    @Test
    void testSaveAndGetScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();

        // 保存脚本
        boolean saveResult = scriptService.save(script);

        // 验证保存结果
        Assertions.assertTrue(saveResult);

        // 根据ID查询脚本
        Script foundScript = scriptService.getById(script.getId());

        // 验证查询结果
        Assertions.assertNotNull(foundScript);
        Assertions.assertEquals(script.getName(), foundScript.getName());
    }

    @Test
    void testUpdateScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();

        // 保存脚本
        boolean saveResult = scriptService.save(script);
        Assertions.assertTrue(saveResult);

        // 更新脚本信息
        String newName = "更新后的测试脚本-" + UUID.randomUUID().toString().substring(0, 8);
        script.setName(newName);
        boolean updateResult = scriptService.updateById(script);

        // 验证更新结果
        Assertions.assertTrue(updateResult);

        // 查询更新后的脚本
        Script updatedScript = scriptService.getById(script.getId());

        // 验证更新结果
        Assertions.assertNotNull(updatedScript);
        Assertions.assertEquals(newName, updatedScript.getName());
    }

    @Test
    void testDeleteScript() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .build();

        // 保存脚本
        boolean saveResult = scriptService.save(script);
        Assertions.assertTrue(saveResult);

        // 删除脚本
        boolean deleteResult = scriptService.removeById(script.getId());

        // 验证删除结果
        Assertions.assertTrue(deleteResult);

        // 验证脚本已被删除
        Script deletedScript = scriptService.getById(script.getId());
        Assertions.assertNull(deletedScript);
    }

    @Test
    void testUploadScript() {
        // 创建测试文件
        String content = "print('Hello, World!')";
        InputStream inputStream = new ByteArrayInputStream(content.getBytes());
        MultipartFile multipartFile = null;
        try {
            multipartFile = new MockMultipartFile(
                    "test_script.py",
                    "test_script.py",
                    "text/plain",
                    inputStream
            );
        } catch (Exception e) {
            Assertions.fail("创建测试文件失败: " + e.getMessage());
        }

        // 调用uploadScript方法
        ScriptVO scriptVO = scriptService.uploadScript(multipartFile);

        // 验证上传结果
        Assertions.assertNotNull(scriptVO);
        Assertions.assertNotNull(scriptVO.getId());
        Assertions.assertNotNull(scriptVO.getName());
        Assertions.assertNotNull(scriptVO.getPath());
    }
}
