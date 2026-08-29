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
 * </ul>
 */
@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
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
                if (lock.tryLock(Duration.ofSeconds(4))) {
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
        lock.unlock();
    }

    @Test
    @DisplayName("分布式锁-Lua校验防止误删他人锁")
    void testUnlock_DoesNotDeleteOthersLock() {
        redisTemplate.delete(RedisKey.SCHEDULER_LOCK_SCAN);

        assertTrue(lock.tryLock(Duration.ofSeconds(10)), "锁空闲时首次获取应成功");

        // 模拟锁已易主：其他实例（不同 token）重写了该 key
        String otherToken = "other-instance-token";
        redisTemplate.opsForValue().set(RedisKey.SCHEDULER_LOCK_SCAN, otherToken, Duration.ofSeconds(10));

        // 旧持有者释放：token 不匹配，Lua 返回 0，不应删除
        lock.unlock();

        Boolean exists = redisTemplate.hasKey(RedisKey.SCHEDULER_LOCK_SCAN);
        assertTrue(Boolean.TRUE.equals(exists), "锁已易主时，旧持有者 unlock 不得误删他人锁");

        redisTemplate.delete(RedisKey.SCHEDULER_LOCK_SCAN);
    }
}
