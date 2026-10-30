package com.cronova.scheduler.service;

import com.cronova.scheduler.domain.message.TaskExecuteMessage;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.route.WorkerRegistry;
import com.cronova.scheduler.route.WorkerSnapshot;
import com.cronova.scheduler.route.strategy.RouteStrategyRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDispatchServiceTest {

    @Mock private WorkerRegistry workerRegistry;
    @Mock private RouteStrategyRouter router;
    @Mock private TaskProducerService taskProducerService;
    @Mock private TaskRunMapper taskRunMapper;

    @Test
    void invalidRequiredTagsFailsClosedInsteadOfDispatchingToAnyWorker() {
        WorkerSnapshot worker = new WorkerSnapshot("w1", "worker-1", Set.of("python"),
                0, 0, 0, WorkerSnapshot.DEFAULT_MAX_CONCURRENT, 0L);
        when(workerRegistry.onlineWorkers()).thenReturn(List.of(worker));
        TaskDispatchService service = new TaskDispatchService(
                workerRegistry, router, taskProducerService, new ObjectMapper(), taskRunMapper);

        Task task = Task.builder().id("task-1").requiredTags("{not-json").build();

        assertThrows(IllegalArgumentException.class,
                () -> service.dispatch(task, TaskExecuteMessage.builder().build(), 0));
        verifyNoInteractions(router, taskProducerService);
    }
}
