package com.sjdt.worker.service;

import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.handler.TaskHandler;
import com.sjdt.worker.model.TaskExecuteMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TaskService#processTask} 幂等接线测试：验证执行入口的抢占行为
 * <ul>
 *     <li>抢占失败（重复消息）：直接 ACK 返回 true，不调用 handler；</li>
 *     <li>抢占成功：正常分发到 handler；</li>
 *     <li>TTL 计算对齐 worker-Python：任务超时 + 60（timeout 为空按 60 计）。</li>
 * </ul>
 */
class TaskServiceClaimTest {

    /** mock 的执行权抢占服务 */
    private RunClaimService runClaimService;

    /** mock 的脚本处理器 */
    private TaskHandler handler;

    /** 被测服务 */
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        runClaimService = mock(RunClaimService.class);
        handler = mock(TaskHandler.class);
        when(handler.supportType()).thenReturn("python");
        when(handler.handle(any())).thenReturn(true);

        ProcessManager processManager = mock(ProcessManager.class);
        TaskInstanceManager instanceManager = mock(TaskInstanceManager.class);
        TaskStatusReporter statusReporter = mock(TaskStatusReporter.class);
        WorkerContext workerContext = mock(WorkerContext.class);
        when(workerContext.getWorkerId()).thenReturn("worker-a");

        taskService = new TaskService(List.of(handler), processManager,
                instanceManager, statusReporter, runClaimService, workerContext);
    }

    /**
     * 构造一条测试任务消息
     *
     * @param timeoutSeconds 任务超时（秒），可传 null
     * @return 任务执行消息
     */
    private TaskExecuteMessage message(Integer timeoutSeconds) {
        return TaskExecuteMessage.builder()
                .taskId("task-1")
                .taskRunId("run-1")
                .scriptType("python")
                .timeoutSeconds(timeoutSeconds)
                .build();
    }

    @Test
    @DisplayName("重复消息-抢占失败直接ACK不执行")
    void testDuplicateMessageSkipped() {
        when(runClaimService.acquireRunClaim(anyString(), anyString(), anyLong())).thenReturn(false);

        Boolean result = taskService.processTask(message(300));

        assertEquals(Boolean.TRUE, result, "重复消息应直接 ACK 返回 true（不 RECONSUME）");
        verify(handler, never()).handle(any());
    }

    @Test
    @DisplayName("首次消息-抢占成功正常分发handler")
    void testFirstMessageDispatched() {
        when(runClaimService.acquireRunClaim(anyString(), anyString(), anyLong())).thenReturn(true);

        Boolean result = taskService.processTask(message(300));

        assertEquals(Boolean.TRUE, result, "首次消息应正常执行");
        verify(handler).handle(any());
    }

    @Test
    @DisplayName("TTL计算-任务超时+60对齐Python版")
    void testClaimTtlAlignedWithPython() {
        when(runClaimService.acquireRunClaim(anyString(), anyString(), anyLong())).thenReturn(true);

        taskService.processTask(message(300));
        verify(runClaimService).acquireRunClaim("run-1", "worker-a", 360); // 300 + 60

        taskService.processTask(message(null));
        verify(runClaimService).acquireRunClaim("run-1", "worker-a", 120); // null -> 60 + 60
    }
}
