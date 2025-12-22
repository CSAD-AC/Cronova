package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.po.Script;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ScriptMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class ScriptMapperTest {

    @Autowired
    private ScriptMapper scriptMapper;

    @Test
    void testSelectById() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入脚本
        scriptMapper.insert(script);

        // 根据ID查询
        Script foundScript = scriptMapper.selectById(script.getId());

        // 验证查询结果
        Assertions.assertNotNull(foundScript);
        Assertions.assertEquals(script.getName(), foundScript.getName());
    }

    @Test
    void testSelectByName() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本2-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test2-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入脚本
        scriptMapper.insert(script);

        // 根据名称查询
        LambdaQueryWrapper<Script> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Script::getName, script.getName());
        Script foundScript = scriptMapper.selectOne(queryWrapper);

        // 验证查询结果
        Assertions.assertNotNull(foundScript);
        Assertions.assertEquals(script.getName(), foundScript.getName());
    }

    @Test
    void testUpdate() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本3-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test3-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入脚本
        scriptMapper.insert(script);

        // 更新脚本信息
        String newName = "更新后的测试脚本3-" + UUID.randomUUID().toString().substring(0, 8);
        script.setName(newName);
        script.setUpdateTime(LocalDateTime.now());
        scriptMapper.updateById(script);

        // 查询更新后的脚本
        Script updatedScript = scriptMapper.selectById(script.getId());

        // 验证更新结果
        Assertions.assertNotNull(updatedScript);
        Assertions.assertEquals(newName, updatedScript.getName());
    }

    @Test
    void testDelete() {
        // 创建测试脚本
        Script script = Script.builder()
                .name("测试脚本4-" + UUID.randomUUID().toString().substring(0, 8))
                .path("scripts/test4-" + UUID.randomUUID().toString().substring(0, 8) + ".py")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入脚本
        scriptMapper.insert(script);

        // 删除脚本
        int result = scriptMapper.deleteById(script.getId());

        // 验证删除结果
        Assertions.assertEquals(1, result);

        // 验证脚本已被删除
        Script deletedScript = scriptMapper.selectById(script.getId());
        Assertions.assertNull(deletedScript);
    }
}
