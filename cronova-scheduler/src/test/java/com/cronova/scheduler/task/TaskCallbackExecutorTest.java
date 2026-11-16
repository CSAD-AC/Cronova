package com.cronova.scheduler.task;

import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.CallbackJobExecuteMessage;
import com.cronova.scheduler.domain.po.CallbackJobRun;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.CallbackJobConfigMapper;
import com.cronova.scheduler.mapper.CallbackJobRunMapper;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.service.ITaskService;
import com.cronova.scheduler.service.ShardSettlementService;
import com.cronova.scheduler.service.TaskRunFinalizer;
import com.cronova.scheduler.util.MinioStorageUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskCallbackExecutorTest {

    @Mock private CallbackJobConfigMapper callbackJobConfigMapper;
    @Mock private CallbackJobRunMapper callbackJobRunMapper;
    @Mock private TaskRunMapper taskRunMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private MinioStorageUtil minioStorageUtil;
    @Mock private RestTemplate restTemplate;
    @Mock private ITaskService taskService;
    @Mock private TaskScheduler callbackRetryScheduler;
    @Mock private TaskRunFinalizer taskRunFinalizer;
    @Mock private ShardSettlementService shardSettlementService;

    @Test
    void executesOneHttpAttemptAndDoesNotRetryLateResult() {
        Executor directExecutor = Runnable::run;
        TaskCallbackExecutor executor = new TaskCallbackExecutor(
                callbackJobConfigMapper, callbackJobRunMapper, taskRunMapper, taskMapper,
                minioStorageUtil, restTemplate, new ObjectMapper(), taskService,
                directExecutor, callbackRetryScheduler, taskRunFinalizer, shardSettlementService);
        TaskRun run = TaskRun.builder()
                .id("run-1").taskId("task-1").workerId("worker-1")
                .status(TaskRunStatus.running).retryNum(0).build();
        when(callbackJobRunMapper.insert(any(CallbackJobRun.class))).thenReturn(1);
        when(minioStorageUtil.storeRequestData(anyString(), anyMap(), anyMap())).thenReturn("request-path");
        when(minioStorageUtil.storeResponseData(anyString(), anyInt(), anyMap(), any())).thenReturn("response-path");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{}"));
        when(taskRunMapper.selectById("run-1")).thenReturn(run);
        when(taskRunFinalizer.tryAdvanceToTerminal(any(TaskRun.class))).thenReturn(false);

        executor.executeCallbackJob(CallbackJobExecuteMessage.builder()
                .taskId("task-1")
                .taskRunId("run-1")
                .configId("config-1")
                .callbackUrl("http://example.test/callback")
                .callbackMethod("POST")
                .secretKey("secret")
                .retryCount(3)
                .retryIntervalSeconds(60)
                .requestData(Map.of("key", "value"))
                .build());

        verify(restTemplate).exchange(
                anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
        verify(shardSettlementService, never()).onRunTerminal(anyString());
        verify(taskService, never()).publishTaskWithRetryAndDelay(anyString(), any(Integer.class),
                any(Integer.class));
    }

    @Test
    void returnsAfterSubmittingCallbackWork() {
        List<Runnable> submitted = new ArrayList<>();
        Executor deferredExecutor = submitted::add;
        TaskCallbackExecutor executor = new TaskCallbackExecutor(
                callbackJobConfigMapper, callbackJobRunMapper, taskRunMapper, taskMapper,
                minioStorageUtil, restTemplate, new ObjectMapper(), taskService,
                deferredExecutor, callbackRetryScheduler, taskRunFinalizer, shardSettlementService);
        when(callbackJobRunMapper.insert(any(CallbackJobRun.class))).thenReturn(1);

        executor.executeCallbackJob(CallbackJobExecuteMessage.builder()
                .taskId("task-1")
                .taskRunId("run-1")
                .configId("config-1")
                .callbackUrl("http://example.test/callback")
                .callbackMethod("POST")
                .build());

        assertEquals(1, submitted.size());
        verifyNoInteractions(restTemplate);
    }

    @Test
    void schedulesTaskRetryWithOriginalBatchIdentity() {
        Executor directExecutor = Runnable::run;
        TaskCallbackExecutor executor = new TaskCallbackExecutor(
                callbackJobConfigMapper, callbackJobRunMapper, taskRunMapper, taskMapper,
                minioStorageUtil, restTemplate, new ObjectMapper(), taskService,
                directExecutor, callbackRetryScheduler, taskRunFinalizer, shardSettlementService);
        TaskRun run = TaskRun.builder()
                .id("run-1").taskId("task-1").workerId("worker-1")
                .status(TaskRunStatus.running).retryNum(0)
                .shardIndex(0).shardTotal(1).shardBatchId("batch-1")
                .build();
        when(callbackJobRunMapper.insert(any(CallbackJobRun.class))).thenReturn(1);
        when(minioStorageUtil.storeRequestData(anyString(), anyMap(), anyMap())).thenReturn("request-path");
        when(minioStorageUtil.storeResponseData(anyString(), anyInt(), anyMap(), any())).thenReturn("response-path");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(500).body("failed"));
        when(taskRunMapper.selectById("run-1")).thenReturn(run);
        when(taskRunFinalizer.tryAdvanceToTerminal(any(TaskRun.class))).thenReturn(true);
        when(taskMapper.selectById("task-1")).thenReturn(Task.builder()
                .id("task-1").retryCount(1).retryIntervalSeconds(5).build());

        executor.executeCallbackJob(CallbackJobExecuteMessage.builder()
                .taskId("task-1")
                .taskRunId("run-1")
                .configId("config-1")
                .callbackUrl("http://example.test/callback")
                .callbackMethod("POST")
                .retryCount(1)
                .retryIntervalSeconds(5)
                .build());

        ArgumentCaptor<Runnable> retryTask = ArgumentCaptor.forClass(Runnable.class);
        verify(callbackRetryScheduler).schedule(retryTask.capture(), any(Instant.class));
        verify(taskService, never()).publishTaskWithRetryAndDelay(
                anyString(), any(Integer.class), any(Integer.class), any(Integer.class),
                any(Integer.class), anyString());
        ArgumentCaptor<CallbackJobRun> callbackRun = ArgumentCaptor.forClass(CallbackJobRun.class);
        verify(callbackJobRunMapper, atLeastOnce()).updateById(callbackRun.capture());
        assertEquals(500, callbackRun.getAllValues().get(callbackRun.getAllValues().size() - 1)
                .getResponseStatusCode());

        retryTask.getValue().run();

        verify(taskService).publishTaskWithRetryAndDelay("task-1", 1, 0, 0, 1, "batch-1");
    }
}
