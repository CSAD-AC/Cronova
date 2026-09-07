package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskRunMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 阻塞策略判定（步骤25）测试
 * <ul>
 *     <li>默认 DISCARD_LATER：上一轮 running -> 本轮产生 skipped 记录并返回 SKIPPED；</li>
 *     <li>COVER_EARLY：停旧放行，返回 PROCEED，不新增丢弃/排队记录；</li>
 *     <li>SERIAL：返回 QUEUED，并产生 queued 记录。</li>
 * </ul>
 * 仅验证判定与落库行为，不触发送往 Worker 的真实 MQ 派发。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class BlockStrategyServiceTest {

    @Autowired
    private BlockStrategyService blockStrategyService;

    @Autowired
    private TaskRunMapper taskRunMapper;

    private final String taskId = "block-test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @AfterEach
    void cleanup() {
        taskRunMapper.delete(new LambdaQueryWrapper<TaskRun>().eq(TaskRun::getTaskId, taskId));
    }

    private Task task(String blockStrategy) {
        return Task.builder()
                .id(taskId)
                .executeType(ExecuteType.script)
                .blockStrategy(blockStrategy)
                .build();
    }

    private void insertRunningSibling() {
        taskRunMapper.insert(TaskRun.builder()
                .taskId(taskId)
                .status(TaskRunStatus.running)
                .startTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .shardIndex(0)
                .shardTotal(1)
                .build());
    }

    private long countStatus(TaskRunStatus status) {
        return taskRunMapper.selectCount(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTaskId, taskId)
                .eq(TaskRun::getStatus, status));
    }

    @Test
    @DisplayName("DISCARD_LATER-上一轮未跑完本轮丢弃")
    void testDiscardLaterCreatesSkipped() {
        insertRunningSibling();
        assertSame(BlockStrategyService.Decision.SKIPPED,
                blockStrategyService.evaluate(task(null)));
        assertEquals(1, countStatus(TaskRunStatus.skipped), "应产生一条skipped记录");
    }

    @Test
    @DisplayName("COVER_EARLY-停旧放行本轮")
    void testCoverEarlyProceeds() {
        insertRunningSibling();
        assertSame(BlockStrategyService.Decision.PROCEED,
                blockStrategyService.evaluate(task(BlockStrategyService.STRATEGY_COVER_EARLY)));
        assertEquals(0, countStatus(TaskRunStatus.skipped));
        assertEquals(0, countStatus(TaskRunStatus.queued));
    }

    @Test
    @DisplayName("SERIAL-上一轮未跑完本轮排队")
    void testSerialCreatesQueued() {
        insertRunningSibling();
        assertSame(BlockStrategyService.Decision.QUEUED,
                blockStrategyService.evaluate(task(BlockStrategyService.STRATEGY_SERIAL)));
        assertEquals(1, countStatus(TaskRunStatus.queued), "应产生一条queued记录");
    }

    @Test
    @DisplayName("无进行中兄弟-放行")
    void testNoActiveSiblingProceeds() {
        assertSame(BlockStrategyService.Decision.PROCEED,
                blockStrategyService.evaluate(task(BlockStrategyService.STRATEGY_SERIAL)));
    }
}