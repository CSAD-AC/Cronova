package com.cronova.scheduler.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.ScriptType;
import com.cronova.scheduler.domain.enums.TaskStatus;
import com.cronova.scheduler.domain.enums.TaskType;
import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.cronova.scheduler.domain.po.CallbackJobRun;
import com.cronova.scheduler.domain.po.Task;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CallbackJobRunMapper测试类
 */
@SpringBootTest
@Transactional
@Rollback
class CallbackJobRunMapperTest {

    @Autowired
    private CallbackJobRunMapper callbackJobRunMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    private Task createTestTask() {
        Task task = Task.builder()
                .name("测试任务-" + UUID.randomUUID().toString().substring(0, 8))
                .scriptType(ScriptType.python)
                .executeType(ExecuteType.script)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskMapper.insert(task);
        return task;
    }

    private CallbackJobConfig createTestConfig(String taskId) {
        CallbackJobConfig config = CallbackJobConfig.builder()
                .taskId(taskId)
                .callbackUrl("https://api.example.com/callback/test-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("test-secret-key-" + UUID.randomUUID().toString().substring(0, 8))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        callbackJobConfigMapper.insert(config);
        return config;
    }

    @Test
    void testSelectById() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 创建测试执行记录
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId("req-" + UUID.randomUUID().toString().substring(0, 8))
                .status("success")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .requestPath("callback-runs/request-" + UUID.randomUUID().toString().substring(0, 8) + ".json")
                .responsePath("callback-runs/response-" + UUID.randomUUID().toString().substring(0, 8) + ".json")
                .responseStatusCode(200)
                .responseTimeMs(150)
                .durationMs(200)
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 根据ID查询
        CallbackJobRun foundRun = callbackJobRunMapper.selectById(run.getId());

        // 验证查询结果
        Assertions.assertNotNull(foundRun);
        Assertions.assertEquals(run.getRequestId(), foundRun.getRequestId());
        Assertions.assertEquals(run.getStatus(), foundRun.getStatus());
        Assertions.assertEquals(200, foundRun.getResponseStatusCode());
    }

    @Test
    void testSelectByRequestId() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 创建测试执行记录
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId(requestId)
                .status("success")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 根据requestId查询
        LambdaQueryWrapper<CallbackJobRun> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CallbackJobRun::getRequestId, requestId);
        CallbackJobRun foundRun = callbackJobRunMapper.selectOne(queryWrapper);

        // 验证查询结果
        Assertions.assertNotNull(foundRun);
        Assertions.assertEquals(requestId, foundRun.getRequestId());
    }

    @Test
    void testSelectByTaskId() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 创建测试执行记录
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId("req-task-" + UUID.randomUUID().toString().substring(0, 8))
                .status("success")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 根据taskId查询
        LambdaQueryWrapper<CallbackJobRun> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CallbackJobRun::getTaskId, task.getId());
        CallbackJobRun foundRun = callbackJobRunMapper.selectOne(queryWrapper);

        // 验证查询结果
        Assertions.assertNotNull(foundRun);
        Assertions.assertEquals(task.getId(), foundRun.getTaskId());
    }

    @Test
    void testSelectByStatus() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 创建测试执行记录
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId("req-status-" + UUID.randomUUID().toString().substring(0, 8))
                .status("failed")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .errorMessage("Connection timeout")
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 根据status查询
        LambdaQueryWrapper<CallbackJobRun> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(CallbackJobRun::getStatus, "failed");
        CallbackJobRun foundRun = callbackJobRunMapper.selectOne(queryWrapper);

        // 验证查询结果
        Assertions.assertNotNull(foundRun);
        Assertions.assertEquals("failed", foundRun.getStatus());
        Assertions.assertEquals("Connection timeout", foundRun.getErrorMessage());
    }

    @Test
    void testUpdate() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 创建测试执行记录
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId("req-update-" + UUID.randomUUID().toString().substring(0, 8))
                .status("running")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 更新执行记录
        run.setStatus("success");
        run.setResponseStatusCode(200);
        run.setResponseTimeMs(300);
        run.setDurationMs(350);
        run.setEndTime(LocalDateTime.now());
        callbackJobRunMapper.updateById(run);

        // 查询更新后的记录
        CallbackJobRun updatedRun = callbackJobRunMapper.selectById(run.getId());

        // 验证更新结果
        Assertions.assertNotNull(updatedRun);
        Assertions.assertEquals("success", updatedRun.getStatus());
        Assertions.assertEquals(200, updatedRun.getResponseStatusCode());
        Assertions.assertEquals(300, updatedRun.getResponseTimeMs());
    }

    @Test
    void testDelete() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 创建测试执行记录
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId("req-delete-" + UUID.randomUUID().toString().substring(0, 8))
                .status("success")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 删除执行记录
        int result = callbackJobRunMapper.deleteById(run.getId());

        // 验证删除结果
        Assertions.assertEquals(1, result);

        // 验证记录已被删除
        CallbackJobRun deletedRun = callbackJobRunMapper.selectById(run.getId());
        Assertions.assertNull(deletedRun);
    }

    @Test
    void testInsertWithRetryIndex() {
        // 1. 创建真实的Task和Config记录
        Task task = createTestTask();
        CallbackJobConfig config = createTestConfig(task.getId());

        // 2. 测试重试场景
        CallbackJobRun run = CallbackJobRun.builder()
                .taskId(task.getId())
                .configId(config.getId())
                .requestId("req-retry-" + UUID.randomUUID().toString().substring(0, 8))
                .status("success")
                .requestUrl(config.getCallbackUrl())
                .requestMethod("POST")
                .retryIndex(2)  // 第3次尝试（0为首次）
                .startTime(LocalDateTime.now().minusSeconds(10))
                .endTime(LocalDateTime.now())
                .durationMs(5000)
                .createTime(LocalDateTime.now())
                .build();

        // 插入执行记录
        callbackJobRunMapper.insert(run);

        // 根据ID查询
        CallbackJobRun foundRun = callbackJobRunMapper.selectById(run.getId());

        // 验证查询结果
        Assertions.assertNotNull(foundRun);
        Assertions.assertEquals(2, foundRun.getRetryIndex());
        Assertions.assertEquals(5000, foundRun.getDurationMs());
    }
}
