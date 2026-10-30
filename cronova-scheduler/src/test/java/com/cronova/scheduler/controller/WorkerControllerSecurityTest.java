package com.cronova.scheduler.controller;

import com.cronova.scheduler.common.Result;
import com.cronova.scheduler.domain.message.WorkerRegisterDTO;
import com.cronova.scheduler.domain.po.AKSK;
import com.cronova.scheduler.domain.po.Worker;
import com.cronova.scheduler.domain.vo.WorkerRegisterVO;
import com.cronova.scheduler.mapper.AKSKMapper;
import com.cronova.scheduler.mapper.TagMapper;
import com.cronova.scheduler.mapper.TaskLogMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import com.cronova.scheduler.mapper.WorkerMapper;
import com.cronova.scheduler.mapper.WorkerTagMapper;
import com.cronova.scheduler.service.IAKSKService;
import com.cronova.scheduler.service.ITaskService;
import com.cronova.scheduler.service.ShardSettlementService;
import com.cronova.scheduler.service.TaskRunFinalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerControllerSecurityTest {

    @Mock private WorkerMapper workerMapper;
    @Mock private AKSKMapper akskMapper;
    @Mock private WorkerTagMapper workerTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private TaskRunMapper taskRunMapper;
    @Mock private TaskLogMapper taskLogMapper;
    @Mock private ITaskService taskService;
    @Mock private IAKSKService akskService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ShardSettlementService shardSettlementService;
    @Mock private TaskRunFinalizer taskRunFinalizer;

    private WorkerController controller;

    @BeforeEach
    void setUp() {
        controller = new WorkerController(workerMapper, akskMapper, workerTagMapper, tagMapper,
                taskRunMapper, taskLogMapper, taskService, akskService, redisTemplate,
                shardSettlementService, taskRunFinalizer);
        ReflectionTestUtils.setField(controller, "workerRegisterToken", "expected-token");
    }

    @Test
    void rejectsRegistrationWhenTokenIsNotConfigured() {
        ReflectionTestUtils.setField(controller, "workerRegisterToken", "");

        Result<WorkerRegisterVO> result = controller.register(registerRequest(), null);

        assertEquals(503, result.getCode());
        verifyNoInteractions(workerMapper, akskMapper, akskService);
    }

    @Test
    void rejectsRegistrationWithWrongToken() {
        Result<WorkerRegisterVO> result = controller.register(registerRequest(), "wrong-token");

        assertEquals(401, result.getCode());
        verifyNoInteractions(workerMapper, akskMapper, akskService);
    }

    @Test
    void createsWorkerAndAkskWithValidToken() {
        when(workerMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            Worker worker = invocation.getArgument(0);
            worker.setId("worker-1");
            return 1;
        }).when(workerMapper).insert(any(Worker.class));

        Result<WorkerRegisterVO> result = controller.register(registerRequest(), "expected-token");

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("worker-1", result.getData().getWorkerId());
        verify(akskMapper).insert(any(AKSK.class));
    }

    @Test
    void regeneratesMissingAkskForExistingWorker() {
        Worker existing = new Worker();
        existing.setId("worker-1");
        when(workerMapper.selectOne(any())).thenReturn(existing);
        when(akskMapper.selectOne(any())).thenReturn(null);
        when(akskService.generateAKSK("worker-1")).thenReturn(AKSK.builder()
                .workerId("worker-1").accessKey("AK-1").secretKey("SK-1").build());

        Result<WorkerRegisterVO> result = controller.register(registerRequest(), "expected-token");

        assertEquals(200, result.getCode());
        assertEquals("AK-1", result.getData().getAccessKey());
        verify(akskService).generateAKSK("worker-1");
    }

    private WorkerRegisterDTO registerRequest() {
        WorkerRegisterDTO dto = new WorkerRegisterDTO();
        dto.setHost("worker-1");
        dto.setIp("127.0.0.1");
        dto.setTags(Set.of("python"));
        return dto;
    }
}
