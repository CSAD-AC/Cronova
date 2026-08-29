# SJDT 调度器改造——原子化实施步骤清单

> **用途**：把《SJDT调度器企业级升级-最终方案》拆成一个个**可独立评审、独立验证**的原子步骤，方便你逐条看懂并审核我的代码。
> **配套文档**：`SJDT调度器企业级升级-最终方案.md`（权威设计）、`调度器核心机制面试要点.md`（概念解释）。
> **每步统一格式**：`目标` / `涉及文件` / `核心实现要点` / `验收标准`。勾选框用于跟踪进度。
> **顺序原则**：步骤按依赖链排列（1→2→3→4→5），**前一步未验收通过，不进入下一步**。

---

## 0. 总览

| 阶段 | 主题 | 步骤 | 优先级 | 解决的问题 |
|---|---|---|---|---|
| 一 | 调度内核高可用（锁 + CAS） | 1 ~ 8 | **P0** | 单点重复触发 / 60s 精度 / 静默漏触发 |
| 二 | 路由策略 + 定向投递修复 | 9 ~ 19 | **P1** | 负载均衡名存实亡 / 定向任务被 ACK 吞掉 |
| 三 | 任务分片 | 20 ~ 23 | **P1** | 大任务单点跑、无法并行与单跑重试 |
| 四 | 阻塞策略 + 故障转移 + 幂等 | 24 ~ 29 | **P2** | Worker 宕机任务滞留 / 重复执行 / 结果覆盖 |
| 五 | 可观测 + 压测 + 工程化 | 30 ~ 32 | **P2** | 拿不出改造前后对比数据 |

**依赖链**：`1~8(内核)` → `9~19(路由)` → `20~23(分片)` → `24~29(可靠性)` → `30~32(观测)`。
关键耦合：分片复用阶段二的"Tag 定向投递"；阻塞/转移依赖阶段二的"路由权收归调度侧"。

---

## 阶段一：调度内核高可用（P0）

### 步骤 1｜数据库 DDL 一次性加列

- [x] **目标**：为 tasks / task_runs 一次性加齐后续所有阶段要用的字段，避免反复改表。

- **涉及文件**：`db/migration/*.sql`（新增）或你现有的建表脚本目录。

- **核心实现要点**：

```sql
ALTER TABLE tasks
    ADD COLUMN next_trigger_time DATETIME(3) NULL COMMENT '下次理论触发时间',
    ADD COLUMN last_trigger_time  DATETIME(3) NULL COMMENT '上次实际触发时间',
    ADD COLUMN route_strategy  VARCHAR(32) NOT NULL DEFAULT 'LEAST_LOAD'
        COMMENT 'ROUND_ROBIN/RANDOM/CONSISTENT_HASH/LEAST_LOAD/FAILOVER/TAG_MATCH/SHARD',
    ADD COLUMN block_strategy VARCHAR(16) NOT NULL DEFAULT 'DISCARD_LATER'
        COMMENT 'SERIAL/DISCARD_LATER/COVER_EARLY',
    ADD COLUMN shard_total INT NULL COMMENT '分片总数,NULL/0=不分片',
    ADD INDEX idx_due (status, task_type, next_trigger_time);

ALTER TABLE task_runs
    ADD COLUMN shard_index INT NULL COMMENT '分片序号',
    ADD COLUMN shard_total INT NULL COMMENT '本批分片总数',
    ADD COLUMN shard_batch_id VARCHAR(64) NULL COMMENT '同一次触发的分片批次ID',
    ADD COLUMN dispatch_time DATETIME(3) NULL COMMENT '实际派发时间',
    ADD COLUMN trigger_latency_ms BIGINT NULL COMMENT '派发时间-理论触发时间';
```

- **验收标准**：SQL 执行成功；`idx_due` 存在；`tasks` / `task_runs` 各字段均出现；旧数据该列均为 NULL（不阻塞）。

---

### 步骤 2｜存量 cron 任务 `next_trigger_time` 回填

- [x] **目标**：存量 `active` cron 任务首轮就能被新扫描命中，不用等人工设置。

- **涉及文件**：新增 `config/NextTriggerTimeBackfillRunner.java`（实现 `ApplicationRunner`）。

- **核心实现要点**：启动时对 `next_trigger_time IS NULL AND task_type='cron' AND status='active'` 的任务，用现有 cronutils 从当前时间算下一个触发点写入；**幂等可重跑**（只处理 NULL 的行）。

- **验收标准**：重启后 `SELECT COUNT(*) FROM tasks WHERE next_trigger_time IS NULL AND task_type='cron' AND status='active'` 为 0；重复重启不报错、不重复计数。

---

### 步骤 3｜实现 Redis 分布式锁组件 `SchedulerLock`

- [x] **目标**：多实例调度时"每轮只允许一个实例干活"，且**不能误删别人的锁**。

- **涉及文件**：新增 `common/SchedulerLock.java`；`common/RedisKey.java` 增加 `LOCK_SCAN` 前缀。

- **核心实现要点**（面试核心考点）：

```java
@Component
public class SchedulerLock {
    private static final String LOCK_KEY = "scheduler:lock:scan";
    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else return 0 end""";
    private final String token = UUID.randomUUID().toString(); // 实例级标识

    public boolean tryLock(Duration ttl) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, ttl)); // SET NX EX
    }
    public void unlock() {
        redisTemplate.execute(unlockScript, List.of(LOCK_KEY), token); // Lua 校验 token 再删
    }
}
```

- 要点：**value 必须是实例 token + 释放用 Lua 校验**——否则实例 A 锁过期、B 拿到锁后，A 的 finally 会把 B 的锁删掉（经典 bug）。
- **验收标准**（可写单测）：并发 20 线程 tryLock，同一时刻只有 1 个成功；持有者 unlock 后他人可获取；**非持有者调 unlock 不影响持有者**（Lua 校验生效）。

---

### 步骤 4｜扫描节奏改造：60s → 5s，接入分布式锁

- [x] **目标**：触发精度从 60s 降到 5s 级；多实例共享扫描权。

- **涉及文件**：`task/ScheduledTaskRunnerTask.java`。

- **核心实现要点**：

```java
@Scheduled(fixedRate = 5000)          // 60_000 -> 5_000
public void runCronTaskScheduler() {
    if (!lock.tryLock(Duration.ofSeconds(4))) return;  // 锁 TTL(4s) < 扫描周期(5s)
    try { cronTaskScheduler.scanAndTrigger(); }
    finally { lock.unlock(); }
}
```

- 要点：**TTL 略小于扫描周期**，宁可偶尔双扫也不能因死锁永久停摆——正确性由步骤 6 的 CAS 兜底（这是锁 + CAS 分工的话术）。
- **验收标准**：单实例日志出现"获取锁/释放锁"；双实例同时启动，每轮只有 1 个实例打印扫描日志。

---

### 步骤 5｜到期驱动扫描 `scanAndTrigger()`（删除全量缓存）

- [x] **目标**：从"全量扫 active + 30 分钟 Redis 缓存"改为"索引命中的到期查询"。

- **涉及文件**：`task/CronTaskScheduler.java`（新增方法）；删除 `TASK_ACTIVE_CACHE` 相关（`RedisKey.java` 常量、`TaskServiceImpl` 两处清缓存、`CronTaskScheduler` 缓存读写）。

- **核心实现要点**：

```java
public void scanAndTrigger() {
    LocalDateTime now = LocalDateTime.now();
    List<Task> due = taskService.list(new LambdaQueryWrapper<Task>()
        .eq(Task::getStatus, TaskStatus.active)
        .eq(Task::getTaskType, TaskType.cron)
        .le(Task::getNextTriggerTime, now.plusSeconds(5)));  // 只查到期行，走 idx_due
    for (Task t : due) { try { trigger(t, now); } catch (Exception e) { log...; } }
}
```

- **删除项说明（面试要会讲"为什么删"）**：旧 `shouldExecuteNow()` 靠"最近理论触发点是否落在 60s 窗口内"判断，重启/扫描延迟超 60s 会**静默漏触发**，且无补偿。
- **验收标准**：`tasks` 表 10 万行 + 只有 5 条到期，SQL 日志显示命中 `idx_due`、仅查 5 行；`TASK_ACTIVE_CACHE` 键不再出现。

---

### 步骤 6｜实现 CAS 抢占触发（trigger 乐观锁）

- [x] **目标**：就算分布式锁全失效、双实例同时扫同一行，也**只允许一次触发成功**（正确性最后防线）。

- **涉及文件**：`task/CronTaskScheduler.java`（`trigger()` 方法）。

- **核心实现要点**：

```java
private void trigger(Task task, LocalDateTime now) {
    LocalDateTime firedAt = task.getNextTriggerTime();
    LocalDateTime next = nextTriggerTime(task.getCronExpr(), firedAt); // 复用 cronutils 解析缓存

    // 步骤 7 的 misfire 判定插入这里（见下）

    int won = taskService.update(new LambdaUpdateWrapper<Task>()
        .eq(Task::getId, task.getId())
        .eq(Task::getNextTriggerTime, firedAt)        // 关键：旧值作乐观锁条件
        .eq(Task::getStatus, TaskStatus.active)
        .set(Task::getNextTriggerTime, next)
        .set(Task::getLastTriggerTime, now));
    if (won == 0) return;                              // 别的实例已触发 -> 直接放弃
    dispatch(task, now);                               // 交给步骤 20 的路由派发入口
}
```

- **双层防重的分工（面试核心话术）**：锁解决**吞吐**（避免 N 个实例空转竞争），CAS 解决**正确性**（同一行的 `next_trigger_time` 旧值条件只允许一个 UPDATE 成功）。xxl-job 用 DB 行锁（`FOR UPDATE` 抢 `xxl_job_lock` 表），原理等价。
- **验收标准**：同一 `firedAt` 双实例并发 trigger，`won` 恰好 1 个为 1、另一个为 0；`next_trigger_time` 只被推进一次。

---

### 步骤 7｜实现 misfire 补偿

- [x] **目标**：任务理论触发点已严重落后（如宕机 10 分钟）时，**放弃补跑、对齐未来**，防止雪崩式补跑。

- **涉及文件**：`task/CronTaskScheduler.java`（`trigger()` 内、CAS 之前）。

- **核心实现要点**：

```java
if (firedAt.isBefore(now.minusMinutes(5))) {          // 落后超过 5 分钟 = misfire
    next = nextTriggerTime(task.getCronExpr(), now);  // 直接对齐到未来
    log.warn("misfire: 任务{}落后{}，跳过补跑对齐至{}", task.getId(), ...);
}
```

- **验收标准**：手工把某任务 `next_trigger_time` 改成 10 分钟前 -> 只触发 1 次对齐日志，`next_trigger_time` 跳到未来，**不**产生 10 次补跑记录。

---

### 步骤 8｜删除旧触发逻辑死代码

- [x] **目标**：删干净旧路径，避免两套逻辑并存误导后续维护。

- **涉及文件**：`CronTaskScheduler.shouldExecuteNow()`、`clearCache()` 关联、`TASK_ACTIVE_CACHE` 全部引用、`ScheduledTaskRunnerTask` 的旧方法名。

- **验收标准**：`grep -r "shouldExecuteNow\|TASK_ACTIVE_CACHE" scheduler-JAVA` 无结果；`mvn compile` 通过。

---

## 阶段二：路由策略 + 定向投递修复（P1）

### 步骤 9｜实现 Worker 在线快照 `WorkerRegistry`

- [ ] **目标**：为路由提供"当前在线 Worker + 实时负载"的统一视图（数据源全部现成）。

- **涉及文件**：新增 `route/WorkerRegistry.java`、`route/WorkerSnapshot.java`。

- **核心实现要点**：

```java
public List<WorkerSnapshot> onlineWorkers() {
    // SCAN（不要用 KEYS *）匹配 heartbeat 前缀 -> 有键即在线
    // 对每个 id HGETALL worker:info:{id} -> 组装 WorkerSnapshot(cpu/mem/runningTasks)
    // 字段解析失败给保守默认值：cpu=0, runningTasks=Integer.MAX_VALUE（宁可不用不可超载）
}
public List<WorkerSnapshot> filterByTags(List<WorkerSnapshot> ws, Set<String> required) {
    // requiredTags ⊆ worker.tags；标签数据已有 worker_tags 表（TAG_MATCH 用）
}
```

- **验收标准**：只列心跳键存活（TTL 30s 内）的 Worker；负载字段与 Redis Hash 一致；离线 Worker 不出现。

---

### 步骤 10｜实现路由策略 SPI + 注册中心

- [ ] **目标**：6 种策略可插拔，新增策略只需实现一个接口。

- **涉及文件**：新增 `route/strategy/RouteStrategy.java`、`route/strategy/RouteStrategyRouter.java`。

- **核心实现要点**：

```java
public interface RouteStrategy {
    String name();   // 与 tasks.route_strategy 存储值一致
    WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates);
}
// Router: Spring 注入所有 RouteStrategy 实现 -> Map<String, RouteStrategy>
```

- **验收标准**：`RouteStrategyRouter` 自动装配出 ≥6 个策略；按 `name()` 能查到对应实现；未知策略名抛明确异常。

---

### 步骤 11｜ROUND_ROBIN 策略

- [ ] **目标**：任务均匀轮流分配给候选 Worker。

- **涉及文件**：新增 `route/strategy/RoundRobinStrategy.java`。

- **核心实现要点**：`Redis INCR scheduler:route:rr` 计数器 `% candidates.size()`，多实例共享游标（解决单机内存计数器无法跨实例的问题）。

- **验收标准**：100 个任务发 3 个 Worker，各 Worker 收到数接近 33/33/34。

---

### 步骤 12｜RANDOM 策略

- [ ] **目标**：随机分配（简单、实现为零状态）。

- **涉及文件**：新增 `route/strategy/RandomStrategy.java`。

- **核心实现要点**：`candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()))`。

- **验收标准**：1000 个任务分布无明显偏斜（各 Worker > 20%）。

---

### 步骤 13｜CONSISTENT_HASH 策略

- [ ] **目标**：同一 taskId 尽量命中同一 Worker；Worker 增减时**迁移最小**。

- **涉及文件**：新增 `route/strategy/ConsistentHashStrategy.java`。

- **核心实现要点**：`TreeMap<Long, Worker>`，每个 Worker 生成 **160 个虚拟节点**（打散热点），`hash(taskId)` 在环上顺时针找第一个；每次调用重建环即可（百级节点开销可忽略）。

- **验收标准**：杀掉 1/3 Worker 后，`taskId -> workerId` 映射变化比例 ≈ 1/3，其余不动（压测可量化）。

---

### 步骤 14｜LEAST_LOAD 策略

- [ ] **目标**：按**真实负载**选最闲的 Worker（比 xxl 的 LFU 只看触发频次更贴近实际）。

- **涉及文件**：新增 `route/strategy/LeastLoadStrategy.java`。

- **核心实现要点**：`score = 0.4*cpu + 0.3*mem + 0.3*100*running/maxConcurrent`，选最小；`maxConcurrent` 取 Worker 可配上限，未配置用保守值。

- **验收标准**：3 个负载不同的进程，高负载 Worker 收到的任务明显更少；权重系数集中在常量便于调参。

---

### 步骤 15｜FAILOVER 策略

- [ ] **目标**：首选 Worker 失联时自动换人，避免单点失败。

- **涉及文件**：新增 `route/strategy/FailoverStrategy.java`。

- **核心实现要点**：候选按心跳新鲜度排序，逐个二次探活（`EXISTS` 心跳 key），选第一个活的；全灭则返回空（由步骤 16 的 park 兜底）。

- **验收标准**：首选 Worker 心跳 key 被手动删除后，任务落到次选 Worker；全灭时返回空并被正确 park。

---

### 步骤 16｜TAG_MATCH 策略

- [ ] **目标**：让 `requiredTags` 真正生效（当前是死字段，没有任何分发逻辑引用）。

- **涉及文件**：新增 `route/strategy/TagMatchStrategy.java`。

- **核心实现要点**：`filterByTags` 后再走 LEAST_LOAD；无匹配 Worker -> 返回空（park 告警）。

- **验收标准**：任务声明 `["python"]`，只被带 python 标签的 Worker 执行；无匹配时任务置 pending + 告警日志。

---

### 步骤 17｜任务 DTO / 实体透传新字段

- [ ] **目标**：前端能配置 `route_strategy / block_strategy / shard_total`，并持久化到 `tasks`。

- **涉及文件**：`Task.java`、`TaskCreateDTO.java`、`TaskUpdateDTO.java`、`TaskController.java`、前端 Vue 表单（可选）。

- **核心实现要点**：三处各加 3 个字段 + 校验（`route_strategy` 限枚举、`shard_total` ≥ 0）；创建/更新时透传落库。

- **验收标准**：接口创建任务时传 `routeStrategy=CONSISTENT_HASH`，DB 中 `route_strategy` 正确存储；非法枚举被拒绝。

---

### 步骤 18｜修复定向投递 Bug（生产端：Tag 定向发送）

- [ ] **目标**：把"盲发 topic"改成"按 workerId 打 Tag 定向投递"。这是**整个阶段二价值最大的一步**。

- **涉及文件**：`service/TaskProducerService.java`（新增 `sendTask(msg, workerId)`）。

- **核心实现要点**（RocketMQ 的 Tag 语法是 `topic:tag`）：

```java
public void sendTask(TaskExecuteMessage msg, String workerId) {
    rocketMQTemplate.syncSend("TASK_EXECUTE:" + workerId,   // topic:tag
            MessageBuilder.withPayload(msg).build(), 3000);
}
```

- **验收标准**：控制台/日志可见消息 Tag = workerId；非目标 Worker 的消费组不再收到该消息。

---

### 步骤 19｜修复定向投递 Bug（消费端：独立消费组 + 按 Tag 订阅）

- [ ] **目标**：从根上消灭"定向任务被非目标 Worker ACK 吞掉"的隐患。

- **Bug 复述（要写进 commit message）**：RocketMQ 集群模式下同组一条消息只投给**一个**消费者；`mq_consumer.py` L87-94 非目标 Worker 收到定向任务直接 `return True`（ACK），消息对全组已消费 -> 任务静默丢失，只能靠 120s 派发超时反复兜底。

- **涉及文件**：`sjdt_worker/app/mq_consumer.py`、`sjdt_worker/app/config.py`。

- **核心实现要点**：

```python
# 1. 消费组改为每 Worker 独立：组内只有本实例
self.task_consumer = PushConsumer(f"{Config.MQ_CONSUMER_GROUP}_{Config.WORKER_ID}")
# 2. 订阅表达式从 "*" 改为自己的 workerId：Broker 侧过滤，别人的消息根本不推给我
self.task_consumer.subscribe(Config.MQ_TASK_TOPIC, self._message_listener,
                             expression=Config.WORKER_ID)
# 3. 删除消费端 targetWorkerId 不匹配就 return True 的分支（L87-94）——路由权收归调度侧
```

- **为什么必须改组名（面试深挖点）**：RocketMQ 要求**同一消费组内所有实例的订阅表达式一致**；若 N 个 Worker 共用一组、各自订阅自己的 id，rebalance 时订阅表互相覆盖、行为未定义。改成"一 Worker 一组"后：组内单实例无 rebalance 问题，Broker 按 Tag 过滤零浪费。
- **前置条件已满足**：Worker 按 host/IP 复用身份（`getByHostOrIp`），workerId 持久化本地，重启不变 -> Tag/组名稳定。
- **验收标准**：3 个 Worker + 100 个定向任务，修复前抽查确认存在丢任务日志；修复后 **100% 由目标 Worker 执行**（日志"定向任务匹配"计数核对）；非目标 Worker 无该消息到达。

---

### 步骤 20｜统一派发入口 `dispatch()`（合并两条发送路径 + park 兜底）

- [ ] **目标**：cron 触发 / 立即执行 / 重试三条派发路径收敛到一个入口，路由结果统一落 Tag。

- **涉及文件**：`task/CronTaskScheduler.java`（`dispatch()`）、`service/impl/TaskServiceImpl.java`（`publishScriptTask` 的选人逻辑改走路由 SPI）。

- **核心实现要点**：

```java
private void dispatch(Task task, LocalDateTime now) {
    List<WorkerSnapshot> online = registry.onlineWorkers();
    if (online.isEmpty()) { parkAndAlert(task); return; }        // 无可用 Worker -> pending + 告警
    WorkerSnapshot worker = router.select(task, online);          // 按 route_strategy 选
    if (worker == null) { parkAndAlert(task); return; }          // 策略无结果（TAG 不匹配等）-> park
    producer.sendTask(message, worker.id());                      // Tag 定向
}
```

- **保留语义**：`task.targetWorker`（用户钉死）存在时路由直接选它；在线校验失败 -> park 告警，不再盲发。
- **顺带修复**：cron 路径原 `executeScriptTask` 忽略 `requiredTags`、不 attach script tags 的问题，一并走 `registry.filterByTags`。
- **验收标准**：手动触发 / cron 触发 / 重试三条路径都打到 `dispatch()`；无 Worker 时 run 置 `pending`，120s 派发超时监控接管。

---

## 阶段三：任务分片（P1）

### 步骤 21｜消息与实体增加分片字段

- [ ] **目标**：让消息能携带分片身份（分片序号 / 总数 / 批次 ID）。

- **涉及文件**：`domain/message/TaskExecuteMessage.java`、`domain/po/TaskRun.java`（DB 列已在步骤 1 加齐）。

- **核心实现要点**：`TaskExecuteMessage` 增加 `shardIndex / shardTotal / shardBatchId`；`TaskRun` 对应 3 个字段（映射 `shard_index/shard_total/shard_batch_id`）。

- **验收标准**：构造消息/插入 run 时能写入 3 个分片字段，反序列化不丢字段。

---

### 步骤 22｜分片触发派发（N 条 Tag 定向消息）

- [ ] **目标**：`shard_total > 0` 的任务触发时，为每片建一条 run、发一条定向消息。

- **涉及文件**：`task/CronTaskScheduler.java`（`trigger()` 内分片分支）、`service/ShardSettlementService.java`（新建，见步骤 23）。

- **核心实现要点**（关键设计决策：**shardTotal 固定，绝不动态缩片**）：

```java
int shardTotal = task.getShardTotal();
if (shardTotal > 0) {
    List<WorkerSnapshot> online = registry.onlineWorkers();
    if (online.isEmpty()) { parkAndAlert(task); return; }
    String batchId = UUID.randomUUID().toString();
    for (int i = 0; i < shardTotal; i++) {
        TaskRun run = createShardRun(task, i, shardTotal, batchId); // 每片一行 task_run
        producer.sendTask(messageWithShard(task, i, shardTotal, batchId),
                          online.get(i % online.size()).id());      // 片数可大于 Worker 数 -> 一台多片
    }
}
```

- **与 xxl-job 的差异（面试点）**：xxl 用"广播触发、执行器按注册序号自认分片"；我们用"N 条 Tag 定向消息"——复用阶段二基础设施，且**天然支持单片失败单片重试**（xxl 广播重试是整体重发）。代价是 N 条消息流量。
- **验收标准**：`shard_total=5` 触发 -> 生成 5 条 run（同一 `shard_batch_id`、`shard_index` 0~4）、5 条 Tag 定向消息；2 个 Worker 时分布为 3/2。

---

### 步骤 23｜Worker 端注入分片环境变量 + 分片结算

- [ ] **目标**：业务脚本能感知自己属于哪一片；全片终态后才汇总。

- **涉及文件**：`sjdt_worker/app/service/task_handler/python_handler.py`、`java_handler.py`、`go_handler.py`、`sjdt_worker/app/service/task_service.py`；新增 `scheduler-JAVA/.../service/ShardSettlementService.java`。

- **核心实现要点**：

```python
# 三个 handler 组装子进程 env 时追加：
env["SJDT_SHARD_INDEX"] = str(task.shardIndex)
env["SJDT_SHARD_TOTAL"] = str(task.shardTotal)
env["SJDT_SHARD_BATCH_ID"] = str(task.shardBatchId)
```

```java
// ShardSettlementService：每片结果到达时（TaskResultConsumer 钩子）
long done = taskRunMapper.selectCount(... .eq(TaskRun::getShardBatchId, batchId)
    .in(TaskRun::getStatus, success, failed, timeout));
if (done == shardTotal) {  // 全部终态 -> 汇总：任一片 failed -> 批次 failed，否则 success
    // 更新所有片 + 发 Dashboard 事件
}
```

- **业务脚本约定**（README 写明）：`hash(数据主键) % SJDT_SHARD_TOTAL == SJDT_SHARD_INDEX` 的行归本片处理。
- **非分片任务即 N=1 特例**：`shardIndex=0, shardTotal=1, batchId=runId`，结算走同一条路径，不写两套逻辑。
- **单片重试不换片**：重试消息只带该片 `shardIndex`，分片由 hash 固定（保证不重不漏处理同一段数据）。
- **验收标准**：1 分片 vs 5 分片 vs 10 分片耗时呈近似线性提升；片间行数互斥（Σ各片 == 总数、两两交集空）；杀 1 片 Worker 后该片重试成功、其他片不受影响。

---

## 阶段四：阻塞策略 + 故障转移 + 幂等（P2）

### 步骤 24｜`TaskRunStatus` 增加 `queued` / `skipped`

- [ ] **目标**：为阻塞策略（SERIAL 排队 / DISCARD_LATER 跳过）提供状态载体。

- **涉及文件**：`domain/enums/TaskRunStatus.java`。

- **核心实现要点**：枚举加 `queued`（排队中）、`skipped`（被阻塞丢弃）；同步检查所有 `switch`/`in(...)` 状态集合是否需要纳入（如超时监控的 `running/pending` 查询）。

- **验收标准**：编译通过；`queued` 状态的 run 不被 120s 派发超时误判（明确排除在超时查询外）。

---

### 步骤 25｜实现阻塞策略判定（DISCARD_LATER / COVER_EARLY / SERIAL）

- [ ] **目标**：同一任务上一轮还没跑完时，按配置决定本轮怎么办。

- **涉及文件**：`task/CronTaskScheduler.java`（`trigger()` 的 `dispatch` 之前）。

- **核心实现要点**：

```java
TaskRun running = taskRunMapper.selectOne(... .eq(TaskRun::getTaskId, task.getId())
    .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending)
    .orderByDesc(TaskRun::getCreateTime).last("LIMIT 1"));
if (running != null) {
    switch (task.getBlockStrategy()) {
        case DISCARD_LATER -> { logRunSkipped(task); return; }            // 报表类默认
        case COVER_EARLY  -> taskProducerService.sendTaskControl(stopMsgFor(running)); // 杀旧派新
        case SERIAL       -> createQueuedRun(task);                        // 排队，等上一轮终态
    }
}
```

- **验收标准**：任务 running 时手动再次触发：DISCARD_LATER 产生 `skipped` run；COVER_EARLY 向旧 run 发 STOP；SERIAL 产生 `queued` run。

---

### 步骤 26｜实现 SERIAL 推进器 `QueuedRunDispatcher`

- [ ] **目标**：`queued` 的 run 在上一个兄弟终态后自动转 `pending` 并派发。

- **涉及文件**：新增 `task/QueuedRunDispatcher.java`（并入 5s 扫描循环）。

- **核心实现要点**：捞 `queued` 状态 run，同 taskId 无 `running/pending` 兄弟时 -> 转 `pending` + `dispatch()`。

- **验收标准**：SERIAL 任务连续触发 3 次，最终 3 个 run **串行**执行（前一个 success 后才开始下一个），无并行。

---

### 步骤 27｜Worker 端 SETNX 执行权抢占（幂等兜底）

- [ ] **目标**：MQ 是 at-least-once，重投/重复消息不能导致脚本重复执行。

- **涉及文件**：`sjdt_worker/app/service/task_service.py`（`process_task` 入口）。

- **核心实现要点**：

```python
# 执行前抢占：key = task:run:claim:{taskRunId}, value = workerId, NX + EX(timeout+60)
claimed = redis.set(f"task:run:claim:{task.taskRunId}", Config.WORKER_ID,
                    nx=True, ex=task.timeoutSeconds + 60)
if not claimed:
    logger.info("runId 已被认领（重复消息），跳过执行")
    return True   # ACK 但不执行，避免重复跑
```

- **验收标准**：同一条消息重投 2 次，脚本只实际执行 1 次（第二次命中 claim 直接 ACK）。

---

### 步骤 28｜结果条件更新状态机（修"旧结果覆盖新状态"竞态）

- [ ] **目标**：迟到/重复的结果不能把已推进的状态改回去；顺带消除重试双触发。

- **涉及文件**：`service/TaskResultConsumer.java`（`insertOrUpdate` 逻辑）。

- **核心实现要点**：

```java
// 把无条件 insertOrUpdate 改为条件更新：只允许从 pending/running 迁移到终态
int updated = taskRunMapper.update(null, new LambdaUpdateWrapper<TaskRun>()
    .eq(TaskRun::getId, taskRun.getId())
    .in(TaskRun::getStatus, TaskRunStatus.pending, TaskRunStatus.running)  // 关键
    .set(... 终态字段 ...));
if (updated == 0) { log.debug("迟到结果，丢弃"); return; }   // 已被转移/超时推进为终态
```

- **联动**：`checkAndTriggerRetry` 只在**条件更新成功**后才触发重试；`timeout` 由 `TaskTimeoutMonitor` 单一路径负责（它重用 runId 重发），避免 `TaskResultConsumer` 与它双触发新 run。
- **验收标准**：模拟"超时监控已把 run 置 timeout 并重发 -> 旧 Worker 迟到 success 结果到达"-> 更新 0 行被丢弃，DB 状态不被覆盖；重试计数不重复增加。

---

### 步骤 29｜Worker 离线主动故障转移

- [ ] **目标**：Worker 宕机后，它上面的 running/pending 任务**主动转移**到其他 Worker，而不是等超时被动回收。

- **涉及文件**：`task/WorkerHeartbeatCheckTask.java`。

- **核心实现要点**：

```java
// 存活检测从 30s 拆为 10s 一轮（DB 信息同步维持 30s 不变），故障发现最差从 30s 降到 10s
List<TaskRun> orphans = taskRunMapper.selectList(... 
    .eq(TaskRun::getWorkerId, offlineWorkerId)
    .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending));
for (TaskRun run : orphans) {
    markFailed(run, "worker offline, failover");  // 条件更新判终态，防与迟到结果竞态
    TaskRun newRun = createRetryRun(run);         // 新建 run，沿用任务自身重试预算
    dispatch(newRun);                             // 重发 -> 路由重新选在线 Worker
}
```

- **双保险**：主动转移（10s 级）+ 现有无心跳超时监控兜底；at-least-once 语义下旧 Worker 迟到结果被步骤 28 的条件更新拒绝。
- **验收标准**：`kill -9` 一个跑着任务的 Worker -> 10s 内其 running 任务被判 failed + 新 run 落到其他 Worker 并执行成功；DB 无残留 running。

---

## 阶段五：可观测 + 压测 + 工程化（P2）

### 步骤 30｜指标与 Dashboard

- [ ] **目标**：能证明改造效果（延迟、成功率、分布、调度集群视图）。

- **涉及文件**：新增 `controller/DashboardController.java`；`task_runs.trigger_latency_ms` 已在步骤 1 埋点；调度器实例自注册 `scheduler:instances`（ZSet，score=心跳时间戳）。

- **核心实现要点**：统计触发延迟 P50/P99、派发成功率、各路由策略命中分布、分片批次视图、丢弃/超时计数；Dashboard 展示调度集群实例数。

- **验收标准**：压测期间 Dashboard 数字持续更新且与日志一致；P99 触发延迟 < 2s。

---

### 步骤 31｜压测脚本与改造前后基线对比

- [ ] **目标**：产出可量化的对比表（**改造前的基线数据必须真实采集存档**，否则对比无从谈起）。

- **涉及文件**：新增 `benchmark/submit_burst.py`。

- **核心实现要点**：双调度实例 + 10 Worker，1000 任务/分钟 × 10 分钟，输出下表：

| 指标 | 目标 | 改造前 |
|---|---|---|
| 重复触发 | 0 | 双实例必重复 |
| 触发延迟 P99 | < 2s | 最差 ~60s |
| 定向投递成功率 | 100% | 存在静默丢失 |
| 10 分片吞吐 | ≥ 6x 单片 | - |

- **验收标准**：脚本可重复运行并落盘结果；每阶段完成打 tag（`v1-ha` / `v2-routing` / `v3-sharding` / `v4-reliability` / `v5-benchmark`）后跑一次对比。

---

### 步骤 32｜docker-compose 编排 + 文档归档

- [ ] **目标**：一套命令起全部环境（MySQL/Redis/RocketMQ+broker/MinIO/scheduler ×2/worker ×3），替代手写 `dev-start.txt`。

- **涉及文件**：新增 `docker-compose.yml`、`examples/shard_demo.py`（分片演示脚本）；更新 `README.md`（架构图 + 压测表 + 分片示例）。

- **验收标准**：`docker compose up` 后两个调度实例 + 三个 Worker 均注册成功，Dashboard 显示 2 个调度实例、3 个在线 Worker。

---

## 附：审核指南（你怎么核对我每步代码）

1. **按依赖顺序审**：每步有独立的"涉及文件 + 验收标准"，你先看验收标准，再对代码。
2. **关注不变量**：
   - 步骤 3/6：锁只保吞吐、CAS 保正确性——**删掉锁系统仍不能重复触发**。
   - 步骤 19：Tag 定向语义必须由 Broker 保证，**消费端不能再用过滤兜底**。
   - 步骤 22：`shardTotal` 一旦配置就**永不漂移**（不动态缩片）。
   - 步骤 28：状态只允许 `pending/running -> 终态` 单向迁移。
3. **每步一个提交**：commit message 写明动机（尤其步骤 18/19 的 Bug 修复单独成 commit）。
4. **变更文件对照表**（最终全量）：

| 文件 | 步骤 |
|---|---|
| `common/SchedulerLock.java`（新） | 3 |
| `common/RedisKey.java` | 3 / 5 / 30 |
| `task/ScheduledTaskRunnerTask.java` | 4 |
| `task/CronTaskScheduler.java` | 5 / 6 / 7 / 8 / 20 / 22 / 25 |
| `route/WorkerRegistry.java` + `route/strategy/*`（新） | 9 ~ 16 |
| `service/TaskProducerService.java` | 18 / 20 |
| `service/TaskResultConsumer.java` | 28 |
| `task/WorkerHeartbeatCheckTask.java` | 29 |
| `task/QueuedRunDispatcher.java`（新） | 26 |
| `service/ShardSettlementService.java`（新） | 22 / 23 |
| `domain/po/Task.java`、`TaskRun.java`、`TaskExecuteMessage.java`、DTO/Controller | 1 / 17 / 21 / 24 |
| `sjdt_worker/app/mq_consumer.py`、`config.py` | 19 |
| `sjdt_worker/app/service/task_service.py`、`task_handler/*.py` ×3 | 23 / 27 |
| `DashboardController.java`（新）、`benchmark/`、`docker-compose.yml`（新） | 30 / 31 / 32 |
| 删除 | `TASK_ACTIVE_CACHE`、`shouldExecuteNow` 相关（步骤 5 / 8） |

---

## 附：明确的"不做"（范围控制，防止你问"为什么没有 X"）

- **不做 xxl 的 GLUE 在线编辑 / 执行器自动注册 UI / 邮件告警平台**——对面试叙事无增益。
- **不做调度中心选主**（Redis 主从 / Quartz 集群那种）：锁 + CAS 已满足"无单点 + 不重复"，讲清"为什么不需要更强一致性"比堆复杂度加分。
- **不做 Redis ZSet 时间轮**：5s 扫描 + 索引在万级任务内延迟已达秒级，ZSet 收益 < 复杂度；作为面试延伸准备即可。
- **不做 xxl 式 HTTP 直连派发**：MQ 已是本系统核心链路（重试=延迟消息、结果回流=MQ），保留 MQ 换来队列持久化 + 原生重投递 + 削峰。
