package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.po.CallbackJobConfig;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.CallbackJobConfigMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CallbackJobConfigService测试类
 * 测试回调任务配置Service的业务逻辑
 */
@SpringBootTest
@Transactional
@Rollback
class CallbackJobConfigServiceImplTest {

    @Autowired
    private ICallbackJobConfigService callbackJobConfigService;

    @Autowired
    private ICallbackJobRunService callbackJobRunService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    /**
     * 测试根据任务ID查询回调配置
     */
    @Test
    void testGetByTaskId() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        List<CallbackJobConfig> configs = callbackJobConfigService.getByTaskId(task.getId());

        Assertions.assertNotNull(configs);
        Assertions.assertEquals(1, configs.size());
        Assertions.assertEquals(task.getId(), configs.get(0).getTaskId());
    }

    /**
     * 测试根据任务ID查询不存在的配置
     */
    @Test
    void testGetByTaskId_NotFound() {
        List<CallbackJobConfig> configs = callbackJobConfigService.getByTaskId("non-existent-task-id");

        Assertions.assertNotNull(configs);
        Assertions.assertTrue(configs.isEmpty());
    }

    /**
     * 测试根据任务ID删除回调配置
     */
    @Test
    void testDeleteByTaskId() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        int deleteCount = callbackJobConfigService.deleteByTaskId(task.getId());

        Assertions.assertEquals(1, deleteCount);

        List<CallbackJobConfig> configs = callbackJobConfigService.getByTaskId(task.getId());
        Assertions.assertTrue(configs.isEmpty());
    }

    /**
     * 测试删除不存在的任务ID对应的配置
     */
    @Test
    void testDeleteByTaskId_NotFound() {
        int deleteCount = callbackJobConfigService.deleteByTaskId("non-existent-task-id");

        Assertions.assertEquals(0, deleteCount);
    }

    /**
     * 测试任务级联删除时清理回调配置
     */
    @Test
    void testCascadeDeleteWithTask() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        List<CallbackJobConfig> beforeDelete = callbackJobConfigService.getByTaskId(task.getId());
        Assertions.assertEquals(1, beforeDelete.size());

        callbackJobConfigService.deleteByTaskId(task.getId());

        List<CallbackJobConfig> afterDelete = callbackJobConfigService.getByTaskId(task.getId());
        Assertions.assertTrue(afterDelete.isEmpty());
    }

    /**
     * 测试同一任务下的多个回调配置
     */
    @Test
    void testMultipleConfigsForSameTask() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config1 = createTestConfig(task.getId());
        config1.setCallbackUrl("https://api1.example.com/callback");
        callbackJobConfigMapper.insert(config1);

        CallbackJobConfig config2 = createTestConfig(task.getId());
        config2.setCallbackUrl("https://api2.example.com/callback");
        callbackJobConfigMapper.insert(config2);

        List<CallbackJobConfig> configs = callbackJobConfigService.getByTaskId(task.getId());

        Assertions.assertNotNull(configs);
        Assertions.assertEquals(2, configs.size());
    }

    private Task createTestTask() {
        return Task.builder()
                .name("测试任务-CallbackJobConfig-" + UUID.randomUUID().toString().substring(0, 8))
                .executeType(ExecuteType.script)
                .scriptType(ScriptType.python)
                .taskType(TaskType.once)
                .status(TaskStatus.active)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    private CallbackJobConfig createTestConfig(String taskId) {
        return CallbackJobConfig.builder()
                .taskId(taskId)
                .callbackUrl("https://api.example.com/callback/test-" + UUID.randomUUID().toString().substring(0, 8))
                .callbackMethod("POST")
                .secretKey("test-secret-key-" + UUID.randomUUID().toString().substring(0, 8))
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }
}
