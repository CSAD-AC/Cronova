package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cronova.scheduler.domain.dto.CallbackJobRunQueryDTO;
import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.ScriptType;
import com.cronova.scheduler.domain.enums.TaskStatus;
import com.cronova.scheduler.domain.enums.TaskType;
import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.cronova.scheduler.domain.po.CallbackJobRun;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.vo.CallbackJobRunVO;
import com.cronova.scheduler.mapper.CallbackJobConfigMapper;
import com.cronova.scheduler.mapper.CallbackJobRunMapper;
import com.cronova.scheduler.mapper.TaskMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CallbackJobRunService测试类
 * 测试回调任务执行记录Service的业务逻辑
 */
@SpringBootTest
@Transactional
@Rollback
class CallbackJobRunServiceImplTest {

    @Autowired
    private ICallbackJobRunService callbackJobRunService;

    @Autowired
    private ICallbackJobConfigService callbackJobConfigService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private CallbackJobConfigMapper callbackJobConfigMapper;

    @Autowired
    private CallbackJobRunMapper callbackJobRunMapper;

    /**
     * 测试根据ID获取执行记录详情
     */
    @Test
    void testGetDetailById() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        CallbackJobRun run = createTestRun(task.getId(), config.getId());
        callbackJobRunMapper.insert(run);

        CallbackJobRunVO detail = callbackJobRunService.getDetailById(run.getId());

        Assertions.assertNotNull(detail);
        Assertions.assertEquals(run.getId(), detail.getId());
        Assertions.assertEquals(run.getRequestId(), detail.getRequestId());
    }

    /**
     * 测试获取不存在的执行记录详情
     */
    @Test
    void testGetDetailById_NotFound() {
        CallbackJobRunVO detail = callbackJobRunService.getDetailById("non-existent-id");

        Assertions.assertNull(detail);
    }

    /**
     * 测试根据任务ID查询执行记录列表
     */
    @Test
    void testGetByTaskId() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        CallbackJobRun run = createTestRun(task.getId(), config.getId());
        callbackJobRunMapper.insert(run);

        List<CallbackJobRunVO> runs = callbackJobRunService.getByTaskId(task.getId());

        Assertions.assertNotNull(runs);
        Assertions.assertEquals(1, runs.size());
        Assertions.assertEquals(task.getId(), runs.get(0).getTaskId());
    }

    /**
     * 测试根据配置ID查询执行记录列表
     */
    @Test
    void testGetByConfigId() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        CallbackJobRun run = createTestRun(task.getId(), config.getId());
        callbackJobRunMapper.insert(run);

        List<CallbackJobRunVO> runs = callbackJobRunService.getByConfigId(config.getId());

        Assertions.assertNotNull(runs);
        Assertions.assertEquals(1, runs.size());
        Assertions.assertEquals(config.getId(), runs.get(0).getConfigId());
    }

    /**
     * 测试根据请求ID查询执行记录
     */
    @Test
    void testGetByRequestId() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        String requestId = "req-test-" + UUID.randomUUID().toString().substring(0, 8);
        CallbackJobRun run = createTestRun(task.getId(), config.getId());
        run.setRequestId(requestId);
        callbackJobRunMapper.insert(run);

        CallbackJobRunVO foundRun = callbackJobRunService.getByRequestId(requestId);

        Assertions.assertNotNull(foundRun);
        Assertions.assertEquals(requestId, foundRun.getRequestId());
    }

    /**
     * 测试分页查询执行记录
     */
    @Test
    void testPageQuery() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        for (int i = 0; i < 5; i++) {
            CallbackJobRun run = createTestRun(task.getId(), config.getId());
            run.setStatus(i % 2 == 0 ? "success" : "failed");
            callbackJobRunMapper.insert(run);
        }

        CallbackJobRunQueryDTO queryDTO = new CallbackJobRunQueryDTO();
        queryDTO.setPageNum(1);
        queryDTO.setPageSize(3);
        queryDTO.setTaskId(task.getId());

        Page<CallbackJobRunVO> page = callbackJobRunService.pageQuery(queryDTO);

        Assertions.assertNotNull(page);
        Assertions.assertEquals(5, page.getTotal());
        Assertions.assertEquals(3, page.getRecords().size());
    }

    /**
     * 测试分页查询按状态筛选
     */
    @Test
    void testPageQuery_WithStatusFilter() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        for (int i = 0; i < 3; i++) {
            CallbackJobRun run = createTestRun(task.getId(), config.getId());
            run.setStatus("success");
            callbackJobRunMapper.insert(run);
        }
        for (int i = 0; i < 2; i++) {
            CallbackJobRun run = createTestRun(task.getId(), config.getId());
            run.setStatus("failed");
            callbackJobRunMapper.insert(run);
        }

        CallbackJobRunQueryDTO queryDTO = new CallbackJobRunQueryDTO();
        queryDTO.setPageNum(1);
        queryDTO.setPageSize(10);
        queryDTO.setStatus("success");

        Page<CallbackJobRunVO> page = callbackJobRunService.pageQuery(queryDTO);

        Assertions.assertNotNull(page);
        Assertions.assertEquals(3, page.getTotal());
        Assertions.assertTrue(page.getRecords().stream()
                .allMatch(r -> "success".equals(r.getStatus())));
    }

    /**
     * 测试获取配置统计信息
     */
    @Test
    void testGetConfigStatistics() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        for (int i = 0; i < 5; i++) {
            CallbackJobRun run = createTestRun(task.getId(), config.getId());
            run.setStatus(i < 3 ? "success" : "failed");
            callbackJobRunMapper.insert(run);
        }

        Map<String, Object> stats = callbackJobRunService.getConfigStatistics(config.getId());

        Assertions.assertNotNull(stats);
        Assertions.assertEquals(5L, stats.get("totalCount"));
        Assertions.assertEquals(3L, stats.get("successCount"));
        Assertions.assertEquals(2L, stats.get("failedCount"));
        Assertions.assertEquals(60.0, stats.get("successRate"));
    }

    /**
     * 测试删除执行记录
     */
    @Test
    void testDeleteById() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        CallbackJobRun run = createTestRun(task.getId(), config.getId());
        callbackJobRunMapper.insert(run);

        boolean deleteResult = callbackJobRunService.deleteById(run.getId());

        Assertions.assertTrue(deleteResult);

        CallbackJobRun deletedRun = callbackJobRunMapper.selectById(run.getId());
        Assertions.assertNull(deletedRun);
    }

    /**
     * 测试根据任务ID删除执行记录
     */
    @Test
    void testDeleteByTaskId() {
        Task task = createTestTask();
        taskMapper.insert(task);

        CallbackJobConfig config = createTestConfig(task.getId());
        callbackJobConfigMapper.insert(config);

        CallbackJobRun run1 = createTestRun(task.getId(), config.getId());
        callbackJobRunMapper.insert(run1);
        CallbackJobRun run2 = createTestRun(task.getId(), config.getId());
        callbackJobRunMapper.insert(run2);

        int deleteCount = callbackJobRunService.deleteByTaskId(task.getId());

        Assertions.assertEquals(2, deleteCount);
    }

    private Task createTestTask() {
        return Task.builder()
                .name("测试任务-CallbackJobRun-" + UUID.randomUUID().toString().substring(0, 8))
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

    private CallbackJobRun createTestRun(String taskId, String configId) {
        return CallbackJobRun.builder()
                .taskId(taskId)
                .configId(configId)
                .requestId("req-" + UUID.randomUUID().toString().substring(0, 8))
                .status("success")
                .requestUrl("https://api.example.com/callback")
                .requestMethod("POST")
                .responseStatusCode(200)
                .responseTimeMs(100)
                .durationMs(150)
                .createTime(LocalDateTime.now())
                .build();
    }
}
