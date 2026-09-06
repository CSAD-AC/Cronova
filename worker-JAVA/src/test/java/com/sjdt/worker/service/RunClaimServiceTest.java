package com.sjdt.worker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RunClaimService} 单元测试：验证 SETNX 执行权抢占（幂等执行）语义
 * <ul>
 *     <li>同一 runId：首次抢占成功、重复抢占失败（幂等）；</li>
 *     <li>不同 runId 互不影响；</li>
 *     <li>runId 为空直接放行；</li>
 *     <li>Redis 异常 fail-open（放行执行，不把任务搞丢）；</li>
 *     <li>TTL 非法时兜底 60s。</li>
 * </ul>
 * 场景与 worker-Python 的 tests/test_redis_utils.py 一一对齐：
 * 不依赖真实 Redis，以 Mockito 状态式 Answer 模拟内存版 SETNX。
 */
class RunClaimServiceTest {

    /** 模拟 Redis 的内存存储（key -> value） */
    private final Map<String, String> store = new HashMap<>();

    private RunClaimService service;

    @BeforeEach
    void setUp() {
        store.clear();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟 SETNX：key 已存在返回 null（失败），不存在则写入并返回 true
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(inv -> {
                    String key = inv.getArgument(0);
                    if (store.containsKey(key)) {
                        return null;
                    }
                    store.put(key, inv.getArgument(1));
                    return true;
                });

        service = new RunClaimService(redisTemplate);
    }

    @Test
    @DisplayName("同一runId-首次抢占成功重复抢占失败（幂等）")
    void testFirstWinsSecondSkipped() {
        String runId = "ut_claim_1";
        assertTrue(service.acquireRunClaim(runId, "worker-a", 60), "首次抢占应成功");
        assertFalse(service.acquireRunClaim(runId, "worker-b", 60), "重复消息的抢占应失败");
    }

    @Test
    @DisplayName("不同runId-互不影响可独立抢占")
    void testDistinctRunIdsIndependent() {
        assertTrue(service.acquireRunClaim("ut_claim_a", "worker-a", 60));
        assertTrue(service.acquireRunClaim("ut_claim_b", "worker-a", 60), "不同runId应可独立抢占");
    }

    @Test
    @DisplayName("runId为空-不抢占直接放行")
    void testEmptyRunIdAllowsExecution() {
        assertTrue(service.acquireRunClaim(null, "worker-a", 60));
        assertTrue(service.acquireRunClaim("", "worker-a", 60));
    }

    @Test
    @DisplayName("Redis异常-fail-open放行执行")
    void testRedisFailureFailsOpen() {
        StringRedisTemplate brokenTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> brokenOps = mock(ValueOperations.class);
        when(brokenTemplate.opsForValue()).thenReturn(brokenOps);
        when(brokenOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection refused"));

        RunClaimService brokenService = new RunClaimService(brokenTemplate);
        assertTrue(brokenService.acquireRunClaim("ut_claim_broken", "worker-a", 60),
                "Redis 不可用时应 fail-open 放行，避免把任务搞丢");
    }

    @Test
    @DisplayName("键名对齐-使用task:run:claim前缀")
    void testClaimKeyPrefixAligned() {
        StringRedisTemplate spyTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> spyOps = mock(ValueOperations.class);
        when(spyTemplate.opsForValue()).thenReturn(spyOps);
        when(spyOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RunClaimService spyService = new RunClaimService(spyTemplate);

        spyService.acquireRunClaim("run-123", "worker-a", 120);

        // 键名必须与 worker-Python / 调度器约定一致，保证两端互斥
        Mockito.verify(spyOps).setIfAbsent(eq("task:run:claim:run-123"), eq("worker-a"), eq(Duration.ofSeconds(120)));
    }

    @Test
    @DisplayName("TTL非法-兜底60秒")
    void testInvalidTtlFallsBackToDefault() {
        StringRedisTemplate spyTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> spyOps = mock(ValueOperations.class);
        when(spyTemplate.opsForValue()).thenReturn(spyOps);
        when(spyOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RunClaimService spyService = new RunClaimService(spyTemplate);

        spyService.acquireRunClaim("run-124", "worker-a", 0);

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        Mockito.verify(spyOps).setIfAbsent(eq("task:run:claim:run-124"), eq("worker-a"), ttlCaptor.capture());
        assertEquals(Duration.ofSeconds(60), ttlCaptor.getValue(), "TTL 非法时应兜底 60s");
    }
}
