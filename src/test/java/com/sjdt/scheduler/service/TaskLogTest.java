package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.message.TaskResultMessage;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.Rollback;

import java.time.LocalDateTime;

/**
 * 任务日志功能测试类
 * 测试调度器接受MinIO日志路径并保存到数据库的功能
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
     * 测试TaskResultConsumer接受并保存MinIO日志路径
     * 测试场景：通过RocketMQ消费者接收任务结果，验证日志路径是否正确保存到task_log表
     */
    @Test
    void testTaskResultConsumerSaveLogPath() {
        // 构建测试任务结果消息
        String taskRunId = "test-task-run-id-" + System.currentTimeMillis();
        String logPath = "minio://logs/" + taskRunId + ".log";
        
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
        
        // 验证task_run表中是否有对应的记录
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该被保存");
        Assertions.assertEquals("test-task-id", taskRun.getTaskId(), "任务ID应该匹配");
        
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
     * 测试场景：发送没有logPath的任务结果，验证不会创建task_log记录
     */
    @Test
    void testTaskResultConsumerWithNullLogPath() {
        // 构建没有logPath的测试任务结果消息
        String taskRunId = "null-log-" + System.currentTimeMillis();
        
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
        
        // 验证task_run表中是否有对应的记录
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该被保存");
        
        // 验证task_log表中是否没有对应的记录（因为logPath为null）
        LambdaQueryWrapper<TaskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskLog::getRunId, taskRunId);
        TaskLog taskLog = taskLogMapper.selectOne(queryWrapper);
        Assertions.assertNull(taskLog, "当logPath为null时，不应该创建任务日志记录");
    }
    
    /**
     * 测试当logPath为空字符串时，不保存到task_log表
     * 测试场景：发送logPath为空字符串的任务结果，验证不会创建task_log记录
     */
    @Test
    void testTaskResultConsumerWithEmptyLogPath() {
        // 构建logPath为空字符串的测试任务结果消息
        String taskRunId = "empty-log-" + System.currentTimeMillis();
        
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
        
        // 验证task_run表中是否有对应的记录
        TaskRun taskRun = taskRunMapper.selectById(taskRunId);
        Assertions.assertNotNull(taskRun, "任务运行记录应该被保存");
        
        // 验证task_log表中是否没有对应的记录（因为logPath为空字符串）
        LambdaQueryWrapper<TaskLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TaskLog::getRunId, taskRunId);
        TaskLog taskLog = taskLogMapper.selectOne(queryWrapper);
        Assertions.assertNull(taskLog, "当logPath为空字符串时，不应该创建任务日志记录");
    }
}