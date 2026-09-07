package com.cronova.scheduler.service;

import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskRunMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 终态赋值器（步骤28）测试：验证状态机单向迁移不变量
 * <ul>
 *     <li>pending/running -> 终态 只允许一次成功；</li>
 *     <li>run 已处于终态后，迟到的结果被丢弃（条件更新 0 行），不覆盖新状态。</li>
 * </ul>
 */
@SpringBootTest
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class TaskRunFinalizerTest {

    @Autowired
    private TaskRunFinalizer taskRunFinalizer;

    @Autowired
    private TaskRunMapper taskRunMapper;

    @Test
    @DisplayName("终态赋值-迟到结果不覆盖已推进的成功状态")
    void testLateResultIsDropped() {
        String id = UUID.randomUUID().toString().replace("-", "");

        // 构造一个 running 的 run
        TaskRun running = TaskRun.builder()
                .id(id)
                .taskId("finalizer-test")
                .status(TaskRunStatus.running)
                .startTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .shardIndex(0)
                .shardTotal(1)
                .build();
        taskRunMapper.insert(running);
        try {
            // 第一次：running -> success 应成功
            TaskRun done = TaskRun.builder()
                    .id(id)
                    .status(TaskRunStatus.success)
                    .exitCode(0)
                    .endTime(LocalDateTime.now())
                    .durationMs(100L)
                    .build();
            assertTrue(taskRunFinalizer.tryAdvanceToTerminal(done), "running->success 应更新成功");
            assertEquals(TaskRunStatus.success, taskRunMapper.selectById(id).getStatus());

            // 第二次：迟到的 failed 结果 -> 更新 0 行，被丢弃，状态保持 success
            TaskRun late = TaskRun.builder()
                    .id(id)
                    .status(TaskRunStatus.failed)
                    .exitCode(-1)
                    .errorMessage("late result")
                    .endTime(LocalDateTime.now())
                    .build();
            assertFalse(taskRunFinalizer.tryAdvanceToTerminal(late), "终态后的迟到结果应被丢弃");
            assertEquals(TaskRunStatus.success, taskRunMapper.selectById(id).getStatus(),
                    "迟到结果不得把已推进的状态改回去");
        } finally {
            taskRunMapper.deleteById(id);
        }
    }
}