package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.enums.TaskStatus;
import com.cronova.scheduler.domain.enums.TaskType;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Worker 离线主动故障转移（步骤29）测试
 * 验证：离线 Worker 名下的 running 孤儿 run 被判 failed，并新建转移 run（沿用分片身份）。
 * 本环境无在线 Worker 时，新 run 以 pending 保留（交派发超时监控兜底），不会发送 MQ，聚焦转移本身。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.flyway.validate-on-migrate=false")
class OrphanTaskFailoverServiceTest {

    @Autowired
    private OrphanTaskFailoverService orphanTaskFailoverService;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskRunMapper taskRunMapper;

    private String taskId;
    private final String workerId = "offline-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        taskId = "failover-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Task task = Task.builder()
                .id(taskId)
                .name("failover-test")
                .executeType(ExecuteType.script)
                .taskType(TaskType.cron)
                .status(TaskStatus.active)
                .retryCount(1)
                .build();
        taskMapper.insert(task);
    }

    @AfterEach
    void cleanup() {
        taskRunMapper.delete(new LambdaQueryWrapper<TaskRun>().eq(TaskRun::getTaskId, taskId));
        taskMapper.deleteById(taskId);
    }

    @Test
    @DisplayName("离线Worker-孤儿run被转移")
    void testTransferOrphans() {
        // 插入一个 running 的孤儿 run（挂在该离线 worker 下）
        TaskRun orphan = TaskRun.builder()
                .taskId(taskId)
                .workerId(workerId)
                .status(TaskRunStatus.running)
                .startTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .shardIndex(0)
                .shardTotal(1)
                .shardBatchId(UUID.randomUUID().toString().replace("-", ""))
                .build();
        taskRunMapper.insert(orphan);

        int transferred = orphanTaskFailoverService.transferOrphans(workerId);

        assertTrue(transferred >= 1, "应转移至少1个孤儿run");

        // 原 run 应判 failed
        assertEquals(TaskRunStatus.failed, taskRunMapper.selectById(orphan.getId()).getStatus(),
                "原孤儿run应被判failed");

        // 应存在一个新的 pending 转移 run，且沿用原分片身份
        List<TaskRun> pendingRuns = taskRunMapper.selectList(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTaskId, taskId)
                .eq(TaskRun::getStatus, TaskRunStatus.pending));
        assertEquals(1, pendingRuns.size(), "应新建一条pending转移run");
        assertEquals(orphan.getShardBatchId(), pendingRuns.get(0).getShardBatchId(),
                "转移run应沿用原分片批次身份（不换片）");
    }
}