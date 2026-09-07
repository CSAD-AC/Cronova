package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.TaskResultMessage;
import com.cronova.scheduler.domain.po.TaskLog;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskLogMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务日志功能测试类
 * 测试调度器接受MinIO日志路径并保存到数据库的功能
 * <p>
 * 注意：阶段四步骤28改造后，{@link TaskResultConsumer#onMessage} 走
 * {@link TaskRunFinalizer} 条件更新状态机——只允许 pending/running 单向迁移到终态，
 * 不存在的 run / 已终态的 run 收到结果一律丢弃（不创建、不回退）。
 * 因此用例必须先预插入 pending 状态的 run，再回流结果验证迁移。
 */
@SpringBootTest
@Transactional
@Rollback
class TaskLogTest {

    @Autowired
    private TaskResultConsumer taskResultConsumer;

    @Autowired
    private TaskLogMapper taskLogMapper;

    @Autowired
    private TaskRunMapper taskRunMapper;

    /**
     * 预插入一条 pending 状态的 run（N=1 特例分片字段，与生产派发路径写入一致），
     * 供 onMessage 结果回流时做条件状态迁移。
     *
     * @param taskRunId 运行实例ID（测试自定义）
     * @param status    预置状态（多数用例 pending；迟到结果用例预置终态）
     * @return 已插入的 run 实体
     */
    private TaskRun insertRun(String taskRunId, TaskRunStatus status) {
        TaskRun run = TaskRun.builder()
                .id(taskRunId)
                .taskId("test-task-id")
                .status(status)
                .startTime(LocalDateTime.now().minusSeconds(30))
                .createTime(LocalDateTime.now())
                .retryNum(0)
                .shardIndex(0)
                .shardTotal(1)
                .shardBatchId(UUID.randomUUID().toString().replace("-", ""))
                .build();
        taskRunMapper.insert(run);
        return run;
    }

    /**
     * 测试TaskResultConsumer接受并保存MinIO日志路径
     * 测试场景：pending run 收到成功结果，状态迁移到 success 且日志路径保存到 task_log 表
     */
    @Test
    void testTaskResultConsumerSaveLogPath() {
        // 预插入 pending 状态的 run（结果回流的前置条件）
        String taskRunId = "test-task-run-id-" + System.currentTimeMillis();
        String logPath = "minio://logs/" + taskRunId + ".log";
        insertRun(taskRunId, TaskRunStatus.pending);

        TaskResultMessage resultMessage = TaskResultMessage.builder()
                .workerId("test-worker-id")
                .taskId("test-task-id")
                .taskRunId(taskRunId)
                .status("success")
                .exitCode(0)
                .startTime(LocalDateTime.now().minusSeconds(30))
                .endTime(LocalDateTime.now())
                .durationMs(30000L)
                .errorMessage(null)
                .logPath(logPath)
                .build();

        // 调用消费者的onMessage方法处理任务结果
        taskResultConsumer.onMessage(resultMessage);

        // 验证task_run记录已迁移到终态 success（条件更新生效）
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该存在");
        Assertions.assertEquals(TaskRunStatus.success, taskRun.getStatus(), "pending run 应迁移到 success");

        // 验证task_log表中是否有对应的记录
        LambdaQueryWrapper<TaskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskLog::getRunId, taskRunId);
        TaskLog taskLog = taskLogMapper.selectOne(queryWrapper);
        Assertions.assertNotNull(taskLog, "任务日志记录应该被保存");
        Assertions.assertEquals(taskRunId, taskLog.getRunId(), "任务运行ID应该匹配");
        Assertions.assertEquals(logPath, taskLog.getStdoutPath(), "stdout日志路径应该匹配");
        Assertions.assertEquals(logPath, taskLog.getStderrPath(), "stderr日志路径应该匹配");
        Assertions.assertNotNull(taskLog.getCreateTime(), "创建时间应该被设置");
    }
    
    /**
     * 测试TaskLogMapper的基本CRUD操作
     * 测试场景：直接使用Mapper操作task_log表，验证基本CRUD功能
     */
    @Test
    void testTaskLogMapperCRUD() {
        // 构建测试任务日志
        String taskRunId = "test-task-run-id-" + System.currentTimeMillis();
        String logPath = "minio://logs/" + taskRunId + ".log";
        
        TaskLog taskLog = TaskLog.builder()
                .runId(taskRunId)
                .stdoutPath(logPath)
                .stderrPath(logPath)
                .createTime(LocalDateTime.now())
                .build();
        
        // 测试插入
        int insertResult = taskLogMapper.insert(taskLog);
        Assertions.assertEquals(1, insertResult, "插入操作应该返回1");
        
        // 测试查询
        TaskLog queriedTaskLog = taskLogMapper.selectById(taskLog.getId());
        Assertions.assertNotNull(queriedTaskLog, "查询结果应该不为空");
        Assertions.assertEquals(taskRunId, queriedTaskLog.getRunId(), "任务运行ID应该匹配");
        Assertions.assertEquals(logPath, queriedTaskLog.getStdoutPath(), "stdout日志路径应该匹配");
        Assertions.assertEquals(logPath, queriedTaskLog.getStderrPath(), "stderr日志路径应该匹配");
        
        // 测试更新
        String newLogPath = "minio://logs/new-" + taskRunId + ".log";
        queriedTaskLog.setStdoutPath(newLogPath);
        queriedTaskLog.setStderrPath(newLogPath);
        int updateResult = taskLogMapper.updateById(queriedTaskLog);
        Assertions.assertEquals(1, updateResult, "更新操作应该返回1");
        
        // 验证更新结果
        TaskLog updatedTaskLog = taskLogMapper.selectById(queriedTaskLog.getId());
        Assertions.assertEquals(newLogPath, updatedTaskLog.getStdoutPath(), "stdout日志路径应该被更新");
        Assertions.assertEquals(newLogPath, updatedTaskLog.getStderrPath(), "stderr日志路径应该被更新");
        
        // 测试删除
        int deleteResult = taskLogMapper.deleteById(queriedTaskLog.getId());
        Assertions.assertEquals(1, deleteResult, "删除操作应该返回1");
        
        // 验证删除结果
        TaskLog deletedTaskLog = taskLogMapper.selectById(queriedTaskLog.getId());
        Assertions.assertNull(deletedTaskLog, "删除后记录应该不存在");
    }
    
    /**
     * 测试当logPath为null时，不保存到task_log表
     * 测试场景：pending run 收到无logPath的成功结果，run 迁移到 success 但不创建 task_log 记录
     */
    @Test
    void testTaskResultConsumerWithNullLogPath() {
        // 预插入 pending 状态的 run
        String taskRunId = "null-log-" + System.currentTimeMillis();
        insertRun(taskRunId, TaskRunStatus.pending);

        TaskResultMessage resultMessage = TaskResultMessage.builder()
                .workerId("test-worker-id")
                .taskId("test-task-id")
                .taskRunId(taskRunId)
                .status("success")
                .exitCode(0)
                .startTime(LocalDateTime.now().minusSeconds(30))
                .endTime(LocalDateTime.now())
                .durationMs(30000L)
                .errorMessage(null)
                .logPath(null) // logPath为null
                .build();

        // 调用消费者的onMessage方法处理任务结果
        taskResultConsumer.onMessage(resultMessage);

        // 验证run已迁移到success（无日志路径不影响状态迁移）
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该存在");
        Assertions.assertEquals(TaskRunStatus.success, taskRun.getStatus(), "pending run 应迁移到 success");

        // 验证task_log表中是否没有对应的记录（因为logPath为null）
        LambdaQueryWrapper<TaskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskLog::getRunId, taskRunId);
        TaskLog taskLog = taskLogMapper.selectOne(queryWrapper);
        Assertions.assertNull(taskLog, "当logPath为null时，不应该创建任务日志记录");
    }

    /**
     * 测试当logPath为空字符串时，不保存到task_log表
     * 测试场景：pending run 收到logPath为空字符串的成功结果，run 迁移到 success 但不创建 task_log 记录
     */
    @Test
    void testTaskResultConsumerWithEmptyLogPath() {
        // 预插入 pending 状态的 run
        String taskRunId = "empty-log-" + System.currentTimeMillis();
        insertRun(taskRunId, TaskRunStatus.pending);

        TaskResultMessage resultMessage = TaskResultMessage.builder()
                .workerId("test-worker-id")
                .taskId("test-task-id")
                .taskRunId(taskRunId)
                .status("success")
                .exitCode(0)
                .startTime(LocalDateTime.now().minusSeconds(30))
                .endTime(LocalDateTime.now())
                .durationMs(30000L)
                .errorMessage(null)
                .logPath("") // logPath为空字符串
                .build();

        // 调用消费者的onMessage方法处理任务结果
        taskResultConsumer.onMessage(resultMessage);

        // 验证run已迁移到success
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该存在");
        Assertions.assertEquals(TaskRunStatus.success, taskRun.getStatus(), "pending run 应迁移到 success");

        // 验证task_log表中是否没有对应的记录（因为logPath为空字符串）
        LambdaQueryWrapper<TaskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskLog::getRunId, taskRunId);
        TaskLog taskLog = taskLogMapper.selectOne(queryWrapper);
        Assertions.assertNull(taskLog, "当logPath为空字符串时，不应该创建任务日志记录");
    }

    /**
     * 测试迟到结果被丢弃（守护步骤28状态机核心行为）
     * 测试场景：run 已被超时监控推进为 timeout，旧 Worker 迟到的 success 结果到达——
     * 条件更新 0 行，状态不回退、不创建 task_log、不重复触发后续流程
     */
    @Test
    void testTaskResultConsumerLateResultDiscarded() {
        // 预插入已终态（timeout）的 run
        String taskRunId = "late-result-" + System.currentTimeMillis();
        String logPath = "minio://logs/" + taskRunId + ".log";
        insertRun(taskRunId, TaskRunStatus.timeout);

        TaskResultMessage resultMessage = TaskResultMessage.builder()
                .workerId("test-worker-id")
                .taskId("test-task-id")
                .taskRunId(taskRunId)
                .status("success")
                .exitCode(0)
                .startTime(LocalDateTime.now().minusSeconds(30))
                .endTime(LocalDateTime.now())
                .durationMs(30000L)
                .errorMessage(null)
                .logPath(logPath)
                .build();

        // 迟到的成功结果到达
        taskResultConsumer.onMessage(resultMessage);

        // 状态保持 timeout，不被迟到的 success 回退
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该存在");
        Assertions.assertEquals(TaskRunStatus.timeout, taskRun.getStatus(), "迟到结果不应把终态 timeout 改回 success");

        // 迟到结果不产生 task_log 记录
        LambdaQueryWrapper<TaskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskLog::getRunId, taskRunId);
        TaskLog taskLog = taskLogMapper.selectOne(queryWrapper);
        Assertions.assertNull(taskLog, "迟到结果不应该创建任务日志记录");
    }
}