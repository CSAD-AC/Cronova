package com.cronova.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SERIAL 排队推进器（步骤26）测试：验证排队 run 的可推进判定与条件推进（queued -> pending）
 * 不触发真实 MQ 派发（scan 的派发环节不在本测试范围内，避免对外发送消息）。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class QueuedRunDispatcherTest {

    @Autowired
    private QueuedRunDispatcher queuedRunDispatcher;

    @Autowired
    private TaskRunMapper taskRunMapper;

    private final String taskId = "queued-test-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @AfterEach
    void cleanup() {
        taskRunMapper.delete(new LambdaQueryWrapper<TaskRun>().eq(TaskRun::getTaskId, taskId));
    }

    private TaskRun queuedRun() {
        return TaskRun.builder()
                .taskId(taskId)
                .status(TaskRunStatus.queued)
                .createTime(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("无进行中兄弟-排队run可推进为pending")
    void testCanPromoteAndAdvance() {
        TaskRun queued = queuedRun();
        taskRunMapper.insert(queued);

        assertTrue(queuedRunDispatcher.canPromote(queued), "无进行中兄弟应可推进");
        assertTrue(queuedRunDispatcher.promoteToPending(queued.getId()), "推进应成功");
        assertEquals(TaskRunStatus.pending, taskRunMapper.selectById(queued.getId()).getStatus());
    }

    @Test
    @DisplayName("存在进行中兄弟-排队run不可推进")
    void testCannotPromoteWhenActiveSibling() {
        insertRunningSibling();
        TaskRun queued = queuedRun();
        taskRunMapper.insert(queued);

        assertFalse(queuedRunDispatcher.canPromote(queued), "存在进行中兄弟时排队run不可推进");
        assertEquals(TaskRunStatus.queued, taskRunMapper.selectById(queued.getId()).getStatus());
    }

    private void insertRunningSibling() {
        taskRunMapper.insert(TaskRun.builder()
                .taskId(taskId)
                .status(TaskRunStatus.running)
                .createTime(LocalDateTime.now())
                .startTime(LocalDateTime.now())
                .build());
    }
}