package com.sjdt.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.Task;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CallbackJobConfigMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class CallbackJobConfigMapperTest {

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Test
    void testSelectById() {
        // 1. 先创建真实的Task记录
        Task task = Task.builder()
                .name("测试任务-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ScriptType.python)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);

        // 2. 创建测试配置（关联真实的taskId）
        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(task.getId())
                .callbackUrl("https://api.example.com/callback/test-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("test-secret-key-" + UUID.randomUUID().toString().substring(0, 8))
                .timeoutSeconds(30)
                .retryCount(2)
                .retryIntervalSeconds(60)
                .totalCallbackCount(0)
                .successCount(0)
                .failedCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入配置
        callbackJobConfigMapper.insert(config);

        // 根据ID查询
        CallbackJobConfig foundConfig = callbackJobConfigMapper.selectById(config.getId());

        // 验证查询结果
        Assertions.assertNotNull(foundConfig);
        Assertions.assertEquals(config.getCallbackUrl(), foundConfig.getCallbackUrl());
        Assertions.assertEquals(config.getSecretKey(), foundConfig.getSecretKey());
    }

    @Test
    void testSelectByTaskId() {
        // 1. 先创建真实的Task记录
        Task task = Task.builder()
                .name("测试任务-task-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ScriptType.python)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);

        // 2. 创建测试配置（关联真实的taskId）
        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(task.getId())
                .callbackUrl("https://api.example.com/callback/test-task-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("test-secret-key-" + UUID.randomUUID().toString().substring(0, 8))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入配置
        callbackJobConfigMapper.insert(config);

        // 根据taskId查询
        LambdaQueryWrapper<CallbackJobConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CallbackJobConfig::getTaskId, task.getId());
        CallbackJobConfig foundConfig = callbackJobConfigMapper.selectOne(queryWrapper);

        // 验证查询结果
        Assertions.assertNotNull(foundConfig);
        Assertions.assertEquals(task.getId(), foundConfig.getTaskId());
    }

    @Test
    void testUpdate() {
        // 1. 先创建真实的Task记录
        Task task = Task.builder()
                .name("测试任务-update-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ScriptType.python)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);

        // 2. 创建测试配置
        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(task.getId())
                .callbackUrl("https://api.example.com/callback/test-update-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("original-secret-key")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入配置
        callbackJobConfigMapper.insert(config);

        // 更新配置信息
        String newUrl = "https://api.example.com/callback/updated-" + UUID.randomUUID().toString().substring(0, 8);
        config.setCallbackUrl(newUrl);
        config.setSecretKey("updated-secret-key");
        config.setUpdateTime(LocalDateTime.now());
        callbackJobConfigMapper.updateById(config);

        // 查询更新后的配置
        CallbackJobConfig updatedConfig = callbackJobConfigMapper.selectById(config.getId());

        // 验证更新结果
        Assertions.assertNotNull(updatedConfig);
        Assertions.assertEquals(newUrl, updatedConfig.getCallbackUrl());
        Assertions.assertEquals("updated-secret-key", updatedConfig.getSecretKey());
    }

    @Test
    void testDelete() {
        // 1. 先创建真实的Task记录
        Task task = Task.builder()
                .name("测试任务-delete-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ScriptType.python)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);

        // 2. 创建测试配置
        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(task.getId())
                .callbackUrl("https://api.example.com/callback/test-delete-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("delete-secret-key")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入配置
        callbackJobConfigMapper.insert(config);

        // 删除配置
        int result = callbackJobConfigMapper.deleteById(config.getId());

        // 验证删除结果
        Assertions.assertEquals(1, result);

        // 验证配置已被删除
        CallbackJobConfig deletedConfig = callbackJobConfigMapper.selectById(config.getId());
        Assertions.assertNull(deletedConfig);
    }

    @Test
    void testUpdateStatistics() {
        // 1. 先创建真实的Task记录
        Task task = Task.builder()
                .name("测试任务-stats-" + UUID.randomUUID().toString().substring(0, 8))
                .type(ScriptType.python)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);

        // 2. 创建测试配置
        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(task.getId())
                .callbackUrl("https://api.example.com/callback/test-stats-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("stats-secret-key")
                .totalCallbackCount(0)
                .successCount(0)
                .failedCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 插入配置
        callbackJobConfigMapper.insert(config);

        // 模拟统计更新
        config.setTotalCallbackCount(10);
        config.setSuccessCount(8);
        config.setFailedCount(2);
        config.setLastCallbackTime(LocalDateTime.now());
        config.setLastCallbackStatus("success");
        callbackJobConfigMapper.updateById(config);

        // 查询更新后的配置
        CallbackJobConfig updatedConfig = callbackJobConfigMapper.selectById(config.getId());

        // 验证统计更新结果
        Assertions.assertEquals(10, updatedConfig.getTotalCallbackCount());
        Assertions.assertEquals(8, updatedConfig.getSuccessCount());
        Assertions.assertEquals(2, updatedConfig.getFailedCount());
        Assertions.assertEquals("success", updatedConfig.getLastCallbackStatus());
    }
}
