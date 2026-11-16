package com.cronova.scheduler.service;

import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.CallbackJobConfigMapper;
import com.cronova.scheduler.mapper.ScriptMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.service.impl.TaskServiceImpl;
import com.cronova.scheduler.task.TaskCallbackExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceCallbackDelayTest {

    @Mock private TaskDispatchService taskDispatchService;
    @Mock private IScriptService scriptService;
    @Mock private ScriptMapper scriptMapper;
    @Mock private TaskRunMapper taskRunMapper;
    @Mock private CallbackJobConfigMapper callbackJobConfigMapper;
    @Mock private ICallbackJobConfigService callbackJobConfigService;
    @Mock private TaskCallbackExecutor taskCallbackExecutor;
    @Mock private TaskScheduler callbackRetryScheduler;

    @Test
    void delaysCallbackPublishAndPreservesBatchIdentity() {
        TaskServiceImpl service = spy(new TaskServiceImpl(
                taskDispatchService, scriptService, scriptMapper, taskRunMapper,
                callbackJobConfigMapper, callbackJobConfigService, taskCallbackExecutor,
                new ObjectMapper()));
        ReflectionTestUtils.setField(service, "callbackRetryScheduler", callbackRetryScheduler);
        Task task = Task.builder()
                .id("task-1")
                .executeType(ExecuteType.callback)
                .retryCount(1)
                .retryIntervalSeconds(30)
                .build();
        CallbackJobConfig config = CallbackJobConfig.builder()
                .id("config-1")
                .taskId("task-1")
                .callbackUrl("http://example.test/callback")
                .callbackMethod("POST")
                .build();
        doReturn(task).when(service).getById("task-1");
        when(callbackJobConfigMapper.selectList(any())).thenReturn(List.of(config));
        when(taskRunMapper.insert(any(TaskRun.class))).thenAnswer(invocation -> {
            TaskRun run = invocation.getArgument(0);
            run.setId("run-2");
            return 1;
        });

        service.publishTaskWithRetryAndDelay("task-1", 1, 30, 0, 1, "batch-1");

        ArgumentCaptor<Runnable> delayedTask = ArgumentCaptor.forClass(Runnable.class);
        verify(callbackRetryScheduler).schedule(delayedTask.capture(), any(Instant.class));
        verify(taskCallbackExecutor, never()).executeCallbackJob(any());

        delayedTask.getValue().run();

        ArgumentCaptor<TaskRun> runCaptor = ArgumentCaptor.forClass(TaskRun.class);
        verify(taskRunMapper).insert(runCaptor.capture());
        assertEquals(0, runCaptor.getValue().getShardIndex());
        assertEquals(1, runCaptor.getValue().getShardTotal());
        assertEquals("batch-1", runCaptor.getValue().getShardBatchId());
        verify(taskCallbackExecutor).executeCallbackJob(any());
    }
}
