package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.mapper.TaskMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskServiceImpl测试类
 */
@SpringBootTest
@Transactional
@Rollback
class TaskServiceImplTest {

    @Autowired
    private ITaskService taskService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private ScriptMapper scriptMapper;

    @Test
    void testUpdateTargetWorkerToNullByWorkerId() {
        // 创建测试Worker ID
        String workerId = "test-worker-task";        

        // 创建多个关联到该Worker的任务
        for (int i = 0; i < 3; i++) {
            Task task = Task.builder()
                    .name("Test Task " + i)
                    .description("Test Task Description " + i)
                    .type(com.sjdt.scheduler.domain.enums.ScriptType.java)
                    .scriptId("test-script-id")
                    .taskType(TaskType.valueOf(TaskType.once.getValue()))
                    .enabled(true)
                    .timeoutSeconds(300)
                    .targetWorker(workerId)
                    .status(TaskStatus.valueOf(TaskStatus.active.getValue()))
                    .createdBy("test-user")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            taskService.save(task);
        }

        // 创建一个不关联到该Worker的任务作为对比
        Task nonAssociatedTask = Task.builder()
                .name("Test Non-Associated Task")
                .description("Test Non-Associated Task Description")
                .type(com.sjdt.scheduler.domain.enums.ScriptType.python)
                .scriptId("test-script-id")
                .taskType(TaskType.valueOf(TaskType.cron.getValue()))
                .cronExpr("0 0 * * *")
                .enabled(true)
                .timeoutSeconds(300)
                .targetWorker("other-worker")
                .status(TaskStatus.valueOf(TaskStatus.active.getValue()))
                .createdBy("test-user")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        taskService.save(nonAssociatedTask);

        // 执行更新操作，将关联到指定Worker的任务的targetWorker设置为null
        taskService.updateTargetWorkerToNullByWorkerId(workerId);

        // 查询所有任务
        List<Task> allTasks = taskService.list();

        // 验证更新结果
        long updatedTasksCount = allTasks.stream()
                .filter(task -> workerId.equals(task.getTargetWorker()))
                .count();
        Assertions.assertEquals(0, updatedTasksCount, "所有关联到指定Worker的任务的targetWorker应该被设置为null");

        // 验证不关联到该Worker的任务没有被修改
        Task foundNonAssociatedTask = taskService.getById(nonAssociatedTask.getId());
        Assertions.assertNotNull(foundNonAssociatedTask);
        Assertions.assertEquals("other-worker", foundNonAssociatedTask.getTargetWorker());
    }

    @Test
    void testSaveAndGetTask() {
        // 创建测试任务
        Task task = Task.builder()
                .name("Test Save Task")
                .description("Test Save Task Description")
                .type(com.sjdt.scheduler.domain.enums.ScriptType.go)
                .scriptId("test-script-id")
                .taskType(TaskType.valueOf(TaskType.once.getValue()))
                .enabled(true)
                .timeoutSeconds(300)
                .targetWorker("test-worker-save")
                .status(TaskStatus.valueOf(TaskStatus.active.getValue()))
                .createdBy("test-user")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        // 保存任务
        boolean saveResult = taskService.save(task);
        Assertions.assertTrue(saveResult, "任务保存失败");
        Assertions.assertNotNull(task.getId(), "任务ID应该被自动生成");

        // 根据ID查询任务
        Task foundTask = taskService.getById(task.getId());
        Assertions.assertNotNull(foundTask, "根据ID查询任务失败");
        Assertions.assertEquals(task.getName(), foundTask.getName());
        Assertions.assertEquals(task.getType(), foundTask.getType());
        Assertions.assertEquals(task.getTargetWorker(), foundTask.getTargetWorker());
    }

    @Test
    void testPublishPublicTask() {
        // 创建测试脚本
        Script script = Script.builder()
                .id("test-script-id")
                .name("Test Script")
                .path("/test/path/script.java")
                .build();
        
        // 保存脚本
        scriptMapper.insert(script);
        
        // 创建测试任务（公共任务，没有指定targetWorker）
        Task task = Task.builder()
                .id("test-task-id")
                .name("Test Public Task")
                .description("Test Public Task Description")
                .type(com.sjdt.scheduler.domain.enums.ScriptType.java)
                .scriptId("test-script-id")
                .taskType(TaskType.valueOf(TaskType.once.getValue()))
                .enabled(true)
                .timeoutSeconds(300)
                .targetWorker(null) // 公共任务，没有指定Worker
                .status(TaskStatus.valueOf(TaskStatus.active.getValue()))
                .createdBy("test-user")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 保存任务
        taskService.save(task);
        
        // 调用任务发布方法
        taskService.publishTask("test-task-id");
        
        // 验证任务存在
        Task foundTask = taskService.getById("test-task-id");
        Assertions.assertNotNull(foundTask, "任务应该存在");
        Assertions.assertEquals("Test Public Task", foundTask.getName());
    }

    @Test
    void testPublishTargetTask() {
        // 创建测试脚本
        Script script = Script.builder()
                .id("test-script-id")
                .name("Test Script")
                .path("/test/path/script.py")
                .build();
        
        // 保存脚本
        scriptMapper.insert(script);
        
        // 创建测试任务（定向任务，指定了targetWorker）
        Task task = Task.builder()
                .id("test-task-id")
                .name("Test Target Task")
                .description("Test Target Task Description")
                .type(com.sjdt.scheduler.domain.enums.ScriptType.python)
                .scriptId("test-script-id")
                .taskType(TaskType.valueOf(TaskType.once.getValue()))
                .enabled(true)
                .timeoutSeconds(300)
                .targetWorker("test-worker-id") // 定向任务，指定了Worker
                .status(TaskStatus.valueOf(TaskStatus.active.getValue()))
                .createdBy("test-user")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 保存任务
        taskService.save(task);
        
        // 调用任务发布方法
        taskService.publishTask("test-task-id");
        
        // 验证任务存在
        Task foundTask = taskService.getById("test-task-id");
        Assertions.assertNotNull(foundTask, "任务应该存在");
        Assertions.assertEquals("Test Target Task", foundTask.getName());
        Assertions.assertEquals("test-worker-id", foundTask.getTargetWorker());
    }

    @Test
    void testPublishTaskWithCron() {
        // 创建测试脚本
        Script script = Script.builder()
                .id("test-script-id")
                .name("Test Script")
                .path("/test/path/script.java")
                .build();
        
        // 保存脚本
        scriptMapper.insert(script);
        
        // 创建测试任务（定时任务）
        Task task = Task.builder()
                .id("test-task-id")
                .name("Test Cron Task")
                .description("Test Cron Task Description")
                .type(com.sjdt.scheduler.domain.enums.ScriptType.java)
                .scriptId("test-script-id")
                .taskType(TaskType.valueOf(TaskType.cron.getValue()))
                .cronExpr("0 0 * * *") // Cron表达式
                .enabled(true)
                .timeoutSeconds(300)
                .targetWorker(null)
                .status(TaskStatus.valueOf(TaskStatus.active.getValue()))
                .createdBy("test-user")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        
        // 保存任务
        taskService.save(task);
        
        // 调用任务发布方法
        taskService.publishTask("test-task-id");
        
        // 验证任务存在
        Task foundTask = taskService.getById("test-task-id");
        Assertions.assertNotNull(foundTask, "任务应该存在");
        Assertions.assertEquals("Test Cron Task", foundTask.getName());
        Assertions.assertEquals("0 0 * * *", foundTask.getCronExpr());
    }
}