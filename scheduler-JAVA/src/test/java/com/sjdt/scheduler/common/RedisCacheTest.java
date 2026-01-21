package com.sjdt.scheduler.common;

import com.sjdt.scheduler.domain.dto.TaskCreateDTO;
import com.sjdt.scheduler.domain.enums.ExecuteType;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.ITaskService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis缓存功能测试类
 * 通过调用Service方法测试Redis缓存的存储、读取、过期、失效等功能
 */
@Slf4j
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379"
})
@Transactional
@Rollback
class RedisCacheTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IScriptService scriptService;

    private static final String TEST_WORKER_ID = "test-worker-redis-001";

    private String testTaskId;
    private String testScriptId;

    @BeforeEach
    void setUp() {
        log.info("开始执行Redis缓存测试用例");
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        log.info("清理测试数据");
        cleanupTestData();
    }

    private void cleanupTestData() {
        try {
            redisTemplate.delete(RedisKey.getWorkerHeartbeatKey(TEST_WORKER_ID));
            redisTemplate.delete(RedisKey.getWorkerInfoKey(TEST_WORKER_ID));
            redisTemplate.delete(RedisKey.TASK_ACTIVE_CACHE);
            if (testScriptId != null) {
                redisTemplate.delete(RedisKey.SCRIPT_CACHE + ":" + testScriptId);
            }
        } catch (Exception e) {
            log.warn("清理测试数据时发生异常", e);
        }
    }

    /**
     * 测试Task创建后活跃任务缓存是否被清理
     */
    @Test
    @DisplayName("Task创建-清理活跃任务缓存")
    void testTaskCreate_ClearsActiveCache() {
        log.info("测试Task创建后活跃任务缓存是否被清理");

        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        valueOps.set(RedisKey.TASK_ACTIVE_CACHE, "test-data", 30, TimeUnit.MINUTES);

        TaskCreateDTO createDTO = TaskCreateDTO.builder()
                .name("测试任务-创建缓存测试")
                .description("测试任务创建是否清理缓存")
                .executeType(ExecuteType.script)
                .scriptType(ScriptType.python)
                .taskType(TaskType.once)
                .timeoutSeconds(300)
                .build();

        testTaskId = taskService.createTask(createDTO);

        Object cacheAfterCreate = valueOps.get(RedisKey.TASK_ACTIVE_CACHE);
        assertNull(cacheAfterCreate, "创建任务后活跃任务缓存应被清理");

        log.info("Task创建清理缓存测试通过 | TaskID: {}", testTaskId);
    }

    /**
     * 测试Task更新后活跃任务缓存是否被清理
     */
    @Test
    @DisplayName("Task更新-清理活跃任务缓存")
    void testTaskUpdate_ClearsActiveCache() {
        log.info("测试Task更新后活跃任务缓存是否被清理");

        TaskCreateDTO createDTO = TaskCreateDTO.builder()
                .name("测试任务-更新缓存测试")
                .description("初始描述")
                .executeType(ExecuteType.script)
                .scriptType(ScriptType.python)
                .taskType(TaskType.once)
                .timeoutSeconds(300)
                .build();
        testTaskId = taskService.createTask(createDTO);

        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        valueOps.set(RedisKey.TASK_ACTIVE_CACHE, "test-data", 30, TimeUnit.MINUTES);

        Task taskToUpdate = taskService.getById(testTaskId);
        taskToUpdate.setDescription("更新后的描述");
        taskService.updateById(taskToUpdate);

        Object cacheAfterUpdate = valueOps.get(RedisKey.TASK_ACTIVE_CACHE);
        assertNull(cacheAfterUpdate, "更新任务后活跃任务缓存应被清理");

        log.info("Task更新清理缓存测试通过");
    }

    /**
     * 测试Task删除后活跃任务缓存是否被清理
     */
    @Test
    @DisplayName("Task删除-清理活跃任务缓存")
    void testTaskDelete_ClearsActiveCache() {
        log.info("测试Task删除后活跃任务缓存是否被清理");

        TaskCreateDTO createDTO = TaskCreateDTO.builder()
                .name("测试任务-删除缓存测试")
                .description("测试删除是否清理缓存")
                .executeType(ExecuteType.script)
                .scriptType(ScriptType.python)
                .taskType(TaskType.once)
                .timeoutSeconds(300)
                .build();
        testTaskId = taskService.createTask(createDTO);

        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        valueOps.set(RedisKey.TASK_ACTIVE_CACHE, "test-data", 30, TimeUnit.MINUTES);

        taskService.removeById(testTaskId);

        Object cacheAfterDelete = valueOps.get(RedisKey.TASK_ACTIVE_CACHE);
        assertNull(cacheAfterDelete, "删除任务后活跃任务缓存应被清理");

        log.info("Task删除清理缓存测试通过");
    }

    /**
     * 测试listActiveCronTasks方法是否正确查询数据库
     */
    @Test
    @DisplayName("活跃任务列表查询-验证数据库查询")
    void testListActiveCronTasks_QueryDatabase() {
        log.info("测试listActiveCronTasks方法是否正确查询数据库");

        TaskCreateDTO createDTO = TaskCreateDTO.builder()
                .name("测试任务-活跃列表查询")
                .description("测试活跃任务列表查询")
                .executeType(ExecuteType.script)
                .scriptType(ScriptType.python)
                .taskType(TaskType.cron)
                .cronExpr("0 0 * * *")
                .timeoutSeconds(300)
                .build();
        testTaskId = taskService.createTask(createDTO);

        var activeTasks = taskService.listActiveCronTasks();

        assertNotNull(activeTasks, "活跃任务列表不应为空");
        boolean found = activeTasks.stream().anyMatch(t -> t.getId().equals(testTaskId));
        assertTrue(found, "创建的活跃任务应在列表中");

        log.info("活跃任务列表查询测试通过 | 查询到 {} 个活跃任务", activeTasks.size());
    }

    /**
     * 测试Script getById方法的缓存命中
     */
    @Test
    @DisplayName("Script查询-缓存命中测试")
    void testScriptGetById_CacheHit() {
        log.info("测试Script getById方法的缓存命中功能");

        Script script = Script.builder()
                .name("test-script-cache")
                .path("/scripts/test-script.sh")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptService.save(script);
        testScriptId = script.getId();

        String cacheKey = RedisKey.SCRIPT_CACHE + ":" + testScriptId;

        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        assertNull(valueOps.get(cacheKey), "初始状态缓存应为空");

        Script firstQuery = scriptService.getById(testScriptId);
        assertNotNull(firstQuery, "首次查询结果不应为空");
        assertEquals(testScriptId, firstQuery.getId(), "查询到的脚本ID应正确");

        Object cachedData = valueOps.get(cacheKey);
        assertNotNull(cachedData, "首次查询后缓存应不为空");

        Script secondQuery = scriptService.getById(testScriptId);
        assertNotNull(secondQuery, "二次查询结果不应为空");

        log.info("Script缓存命中测试通过");
    }

    /**
     * 测试Script updateById方法清理缓存
     */
    @Test
    @DisplayName("Script更新-清理缓存测试")
    void testScriptUpdate_ClearsCache() {
        log.info("测试Script updateById方法是否清理缓存");

        Script script = Script.builder()
                .name("test-script-update")
                .path("/scripts/test-script.sh")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptService.save(script);
        testScriptId = script.getId();

        String cacheKey = RedisKey.SCRIPT_CACHE + ":" + testScriptId;
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        scriptService.getById(testScriptId);
        assertNotNull(valueOps.get(cacheKey), "查询后缓存应不为空");

        script.setName("updated-script-name");
        scriptService.updateById(script);

        Object cacheAfterUpdate = valueOps.get(cacheKey);
        assertNull(cacheAfterUpdate, "更新后缓存应被清理");

        log.info("Script更新清理缓存测试通过");
    }

    /**
     * 测试Script removeById方法清理缓存
     */
    @Test
    @DisplayName("Script删除-清理缓存测试")
    void testScriptDelete_ClearsCache() {
        log.info("测试Script removeById方法是否清理缓存");

        Script script = Script.builder()
                .name("test-script-delete")
                .path("/scripts/test-script.sh")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        scriptService.save(script);
        testScriptId = script.getId();

        String cacheKey = RedisKey.SCRIPT_CACHE + ":" + testScriptId;
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        scriptService.getById(testScriptId);
        assertNotNull(valueOps.get(cacheKey), "查询后缓存应不为空");

        scriptService.removeById(testScriptId);

        Object cacheAfterDelete = valueOps.get(cacheKey);
        assertNull(cacheAfterDelete, "删除后缓存应被清理");

        log.info("Script删除清理缓存测试通过");
    }

    /**
     * 测试Worker心跳缓存功能
     */
    @Test
    @DisplayName("Worker心跳-缓存存储测试")
    void testWorkerHeartbeat_CacheStorage() {
        log.info("测试Worker心跳缓存的存储功能");

        String heartbeatKey = RedisKey.getWorkerHeartbeatKey(TEST_WORKER_ID);
        String infoKey = RedisKey.getWorkerInfoKey(TEST_WORKER_ID);

        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        valueOps.set(heartbeatKey, "online", 30, TimeUnit.SECONDS);
        hashOps.put(infoKey, "lastHeartbeat", LocalDateTime.now().toString());
        hashOps.put(infoKey, "cpuUsage", 45.0);
        hashOps.put(infoKey, "memUsage", 60.0);
        hashOps.put(infoKey, "runningTasks", 3);

        Object heartbeatValue = valueOps.get(heartbeatKey);
        assertEquals("online", heartbeatValue, "心跳值应为online");

        Object cpuUsage = hashOps.get(infoKey, "cpuUsage");
        assertNotNull(cpuUsage, "CPU使用率应存在");

        log.info("Worker心跳缓存存储测试通过");
    }

    /**
     * 测试Worker心跳缓存过期功能
     */
    @Test
    @DisplayName("Worker心跳-缓存过期测试")
    void testWorkerHeartbeat_Expiration() {
        log.info("测试Worker心跳缓存的过期功能");

        String heartbeatKey = RedisKey.getWorkerHeartbeatKey(TEST_WORKER_ID);
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        valueOps.set(heartbeatKey, "online", 2, TimeUnit.SECONDS);

        Object heartbeatBeforeExpiration = valueOps.get(heartbeatKey);
        assertNotNull(heartbeatBeforeExpiration, "过期前心跳缓存应不为空");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Object heartbeatAfterExpiration = valueOps.get(heartbeatKey);
        assertNull(heartbeatAfterExpiration, "过期后心跳缓存应为空");

        log.info("Worker心跳缓存过期测试通过 | 预期过期时间: 2秒");
    }

    /**
     * 测试Worker心跳过期后状态检查逻辑
     */
    @Test
    @DisplayName("Worker心跳-过期后状态检查测试")
    void testWorkerHeartbeatCheck_AfterExpiration() {
        log.info("测试Worker心跳过期后的状态检查逻辑");

        String heartbeatKey = RedisKey.getWorkerHeartbeatKey(TEST_WORKER_ID);
        String infoKey = RedisKey.getWorkerInfoKey(TEST_WORKER_ID);
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        HashOperations<String, Object, Object> hashOps = redisTemplate.opsForHash();

        valueOps.set(heartbeatKey, "online", 2, TimeUnit.SECONDS);
        hashOps.put(infoKey, "lastHeartbeat", LocalDateTime.now().toString());
        hashOps.put(infoKey, "cpuUsage", 25.0);
        hashOps.put(infoKey, "memUsage", 40.0);

        Boolean isAliveBefore = redisTemplate.hasKey(heartbeatKey);
        assertTrue(isAliveBefore, "过期前心跳键应存在");

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Boolean isAliveAfter = redisTemplate.hasKey(heartbeatKey);
        assertFalse(isAliveAfter, "过期后心跳键应不存在");

        Boolean infoKeyExists = redisTemplate.hasKey(infoKey);
        assertTrue(infoKeyExists, "Worker信息Hash键在心跳过期后仍应存在");

        log.info("Worker心跳过期后状态检查测试通过");
    }

    /**
     * 测试Redis缓存TTL设置和获取
     */
    @Test
    @DisplayName("Redis缓存-TTL设置获取测试")
    void testCacheTTL_SetAndGet() {
        log.info("测试Redis缓存TTL设置和获取功能");

        String testKey = "test:ttl:" + System.currentTimeMillis();
        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();

        valueOps.set(testKey, "test-value", Duration.ofSeconds(30));

        Long ttl = redisTemplate.getExpire(testKey, TimeUnit.SECONDS);
        assertNotNull(ttl, "TTL值不应为空");
        assertTrue(ttl > 0 && ttl <= 30, "TTL值应在合理范围内");

        redisTemplate.delete(testKey);
        log.info("Redis缓存TTL测试通过 | TTL: {}秒", ttl);
    }

    /**
     * 测试updateTargetWorkerToNullByWorkerId方法清理缓存
     */
    @Test
    @DisplayName("Worker离线-清理任务缓存测试")
    void testWorkerOffline_ClearsTaskCache() {
        log.info("测试Worker离线时是否清理任务活跃缓存");

        String workerId = "test-worker-offline-" + System.currentTimeMillis();

        ValueOperations<String, Object> valueOps = redisTemplate.opsForValue();
        valueOps.set(RedisKey.TASK_ACTIVE_CACHE, "test-data", 30, TimeUnit.MINUTES);

        taskService.updateTargetWorkerToNullByWorkerId(workerId);

        Object cacheAfterWorkerOffline = valueOps.get(RedisKey.TASK_ACTIVE_CACHE);
        assertNull(cacheAfterWorkerOffline, "Worker离线后任务活跃缓存应被清理");

        log.info("Worker离线清理任务缓存测试通过");
    }
}
