package com.cronova.scheduler.task;

import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.service.ITaskService;
import com.cronova.scheduler.service.ShardSettlementService;
import com.cronova.scheduler.service.TaskProducerService;
import com.cronova.scheduler.service.TaskRunFinalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskTimeoutMonitorTest {

    @Mock private TaskRunMapper taskRunMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private ITaskService taskService;
    @Mock private TaskProducerService taskProducerService;
    @Mock private TaskRunFinalizer taskRunFinalizer;
    @Mock private ShardSettlementService shardSettlementService;

    private TaskTimeoutMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new TaskTimeoutMonitor(taskRunMapper, taskMapper, taskService,
                taskProducerService, taskRunFinalizer, shardSettlementService);
        ReflectionTestUtils.setField(monitor, "dispatchTimeoutSeconds", 1);
    }

    @Test
    void doesNotOverwriteRunWhenFinalizerRejectsTimeout() {
        TaskRun pending = pendingRun();
        when(taskRunMapper.selectList(any())).thenReturn(List.of(pending));
        when(taskMapper.selectById("task-1")).thenReturn(Task.builder().id("task-1").retryCount(0).build());
        when(taskRunFinalizer.tryAdvanceToTerminal(any())).thenReturn(false);

        monitor.checkTimeoutTasks();

        verify(taskRunMapper, never()).updateById(any(TaskRun.class));
        verify(shardSettlementService, never()).onRunTerminal(any());
    }

    @Test
    void finalizerOwnsTimeoutWriteWhenTransitionSucceeds() {
        TaskRun pending = pendingRun();
        when(taskRunMapper.selectList(any())).thenReturn(List.of(pending));
        when(taskMapper.selectById("task-1")).thenReturn(Task.builder().id("task-1").retryCount(0).build());
        when(taskRunFinalizer.tryAdvanceToTerminal(any())).thenReturn(true);

        monitor.checkTimeoutTasks();

        verify(taskRunMapper, never()).updateById(any(TaskRun.class));
        verify(shardSettlementService).onRunTerminal("run-1");
    }

    private TaskRun pendingRun() {
        return TaskRun.builder()
                .id("run-1")
                .taskId("task-1")
                .status(TaskRunStatus.pending)
                .createTime(LocalDateTime.now().minusSeconds(10))
                .build();
    }
}
