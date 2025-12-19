package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.TaskStatus;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.po.Task;
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

    @Test
    void testUpdateTargetWorkerToNullByWorkerId() {
        // 创建测试Worker ID
        String workerId = "test-worker-task";        

        // 创建多个关联到该Worker的任务
        for (int i = 0; i < 3; i++) {
            Task task = Task.builder()
                    .name("Test Task " + i)
                    .description("Test Task Description " + i)
                    .type(com.sjdt.scheduler.domain.enums.ScriptType.JAVA)
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
                .type(com.sjdt.scheduler.domain.enums.ScriptType.PYTHON)
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
                .type(com.sjdt.scheduler.domain.enums.ScriptType.GO)
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
}