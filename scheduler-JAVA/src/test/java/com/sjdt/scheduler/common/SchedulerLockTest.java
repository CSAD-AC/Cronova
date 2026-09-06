package com.sjdt.scheduler.common;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式锁测试：验证两个核心不变量
 * <ul>
 *     <li>并发获取：同一时刻恰好一个实例成功（SETNX 语义）；</li>
 *     <li>释放防误删：锁已易主时，旧持有者 unlock 不能删掉新持有者的锁（Lua 校验 token）。</li>
 *     <li>多 key 隔离：不同职责锁（scan/heartbeat/timeout/queued）互不阻塞，释放互不影响。</li>
 * </ul>
 */
@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.flyway.validate-on-migrate=false"
})
class SchedulerLockTest {

    @Autowired
    private SchedulerLock lock;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("分布式锁-并发获取恰好一个成功")
    void testConcurrentTryLock_OnlyOneSucceeds() {
        redisTemplate.delete(RedisKey.SCHEDULER_LOCK_SCAN);

        int threads = 20;
        AtomicInteger success = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (lock.tryLock(RedisKey.SCHEDULER_LOCK_SCAN, Duration.ofSeconds(4))) {
                    success.incrementAndGet();
                }
            });
            t.start();
            ts.add(t);
        }
        start.countDown();
        for (Thread t : ts) {
            try {
                t.join();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        assertEquals(1, success.get(), "并发获取分布式锁同一时刻应恰好一个成功");
        lock.unlock(RedisKey.SCHEDULER_LOCK_SCAN);
    }

    @Test
    @DisplayName("分布式锁-Lua校验防止误删他人锁")
    void testUnlock_DoesNotDeleteOthersLock() {
        redisTemplate.delete(RedisKey.SCHEDULER_LOCK_SCAN);

        assertTrue(lock.tryLock(RedisKey.SCHEDULER_LOCK_SCAN, Duration.ofSeconds(10)), "锁空闲时首次获取应成功");

        // 模拟锁已易主：其他实例（不同 token）重写了该 key
        String otherToken = "other-instance-token";
        redisTemplate.opsForValue().set(RedisKey.SCHEDULER_LOCK_SCAN, otherToken, Duration.ofSeconds(10));

        // 旧持有者释放：token 不匹配，Lua 返回 0，不应删除
        lock.unlock(RedisKey.SCHEDULER_LOCK_SCAN);

        Boolean exists = redisTemplate.hasKey(RedisKey.SCHEDULER_LOCK_SCAN);
        assertTrue(Boolean.TRUE.equals(exists), "锁已易主时，旧持有者 unlock 不得误删他人锁");

        redisTemplate.delete(RedisKey.SCHEDULER_LOCK_SCAN);
    }

    @Test
    @DisplayName("分布式锁-不同职责key互不阻塞且释放互不影响")
    void testDifferentKeysIndependent() {
        String keyA = "scheduler:lock:ut_a";
        String keyB = "scheduler:lock:ut_b";
        String keyC = "scheduler:lock:ut_c";
        redisTemplate.delete(java.util.List.of(keyA, keyB, keyC));
        try {
            assertTrue(lock.tryLock(keyA, Duration.ofSeconds(10)), "keyA 首次获取应成功");
            assertTrue(lock.tryLock(keyB, Duration.ofSeconds(10)), "不同 key 的锁应互不阻塞");
            assertTrue(lock.tryLock(keyC, Duration.ofSeconds(10)), "第三个 key 也应可获取");

            // 释放其中一个 key 不应影响其他 key
            lock.unlock(keyB);
            assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(keyA)), "释放keyB不得影响keyA");
            assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(keyC)), "释放keyB不得影响keyC");
        } finally {
            lock.unlock(keyA);
            lock.unlock(keyB);
            lock.unlock(keyC);
            redisTemplate.delete(java.util.List.of(keyA, keyB, keyC));
        }
    }

    @Test
    @DisplayName("分布式锁-多key下非持有者不能删他人锁")
    void testUnlockKeyDoesNotDeleteOthersHolder() {
        String key = "scheduler:lock:ut_exlock";
        redisTemplate.delete(key);
        try {
            assertTrue(lock.tryLock(key, Duration.ofSeconds(10)), "首次获取应成功");
            // 构造另一个实例（不同实例级 token），模拟他人尝试释放本实例持有的锁
            SchedulerLock otherInstance = new SchedulerLock(redisTemplate);
            otherInstance.unlock(key);
            assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(key)),
                    "非持有者（不同token）释放不得删除他人的锁");
            // 真正持有者释放应成功
            lock.unlock(key);
            assertTrue(!Boolean.TRUE.equals(redisTemplate.hasKey(key)), "持有者释放后锁应被删除");
        } finally {
            lock.unlock(key);
            redisTemplate.delete(key);
        }
    }
}
