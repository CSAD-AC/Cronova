# SJDT 调度器企业级升级——最终方案（整合版）

> **本文为唯一权威版本**，整合自原《SJDT调度器企业级升级-详细设计》与《简历优化与调度器企业级升级方案》中的调度器部分（两份旧文档已删除，勿再参考）。
> 结构：现状诊断（为什么改）→ 总体架构（顶层改什么）→ 五阶段实现设计（怎么改：DDL / 代码骨架 / 逐文件改动 / 验证步骤）→ 排期 → 面试问答。
> 工程约定：`com.sjdt.scheduler` 包、MyBatis-Plus（`LambdaQueryWrapper`/`LambdaUpdateWrapper`）、`RedisTemplate<String,Object>`、`RocketMQTemplate`；Worker 端为 `rocketmq-client-python`。
> 参考对象：xxl-job 2.4.x（调度中心集群 + 路由策略 + 分片广播 + 阻塞策略）。

> **设计决策记录（已确认，不再摇摆）**
> 1. 分布式锁：**Redis SETNX + Lua 释放**（正确性由 CAS 保，锁只保吞吐；不引入 DB 行锁，不选无锁方案）
> 2. 分片总数：**任务上配置固定值**（数据分片永不漂移，Worker 上下线只影响哪台跑哪片；片数可大于 Worker 数，一台跑多片是常态）
> 3. 分片记录粒度：**一片一条 task_run**（复用现有执行链路，加 `shard_index/batch_id` 字段；非分片任务统一按 N=1 处理，结算代码单条路径）
> 4. 故障转移时机：**心跳断即转移**（掉线 Worker 的在跑 run 直接判终态 + 新建 run 重发，不与旧执行权纠缠；at-least-once 语义，重复由 Worker 端 SETNX 兜底）

---

## 0. 现状代码级诊断（为什么必须改）

### 0.1 逐链路体检（关键实现逐个读过）

| 现状 | 代码位置 | 问题 |
|------|----------|------|
| 调度触发：单机 `@Scheduled(fixedRate=60000)` 轮询 + 全量扫描活跃任务 | `ScheduledTaskRunnerTask` -> `CronTaskScheduler.scanAndExecuteTasks()` | **① 单点**：多实例部署会重复触发（全项目无任何分布式锁/CAS，已 grep 验证）；**② 精度 60s**；③ `shouldExecuteNow` 用"最近一次理论触发点是否落在 60s 窗口内"判断，扫描延迟或重启超 60s 会**静默漏触发**，无 misfire 补偿 |
| 任务下发：发到 `TASK_EXECUTE` topic，所有 Worker 共用一个消费者组（`WORKER_EXECUTOR_GROUP`，集群模式） | `TaskProducerService.sendTask()`；`sjdt_worker/app/mq_consumer.py` | **真 Bug**：定向任务靠消费端判断 `targetWorkerId` 不匹配就 `return True`（ACK）。RocketMQ 集群模式下一条消息只投给组内**一个**消费者——定向任务可能被非目标 Worker ACK 吞掉，任务永远不执行，只能靠 120s 派发超时兜底反复重试。绝佳的"我发现并修复了线上隐患"面试素材 |
| Worker 负载数据：Redis Hash `scheduler:worker:info:{workerId}` 已采集 cpuUsage / memUsage / runningTasks，并同步到 DB | `WorkerHeartbeatCheckTask.syncWorkerInfoFromRedis()` | 数据采集了但**没用于任何路由决策**——"负载均衡"目前实际只是 MQ 集群消费的随机摊派 |
| 标签路由：`Task.requiredTags`（JSON）与 Worker 标签上报链路都存在 | `Task.java`、`tag_report_service.py`、`WorkerTag` | `requiredTags` 是死字段，没有任何分发逻辑引用它（已 grep 验证） |
| 控制消息 topic 已是**广播模式** | `mq_consumer.py`（`MessageModel.BROADCASTING`） | 现成基础设施，阶段四 COVER_EARLY 的停止指令直接复用 |
| 重试：RocketMQ 延迟级别 + 派发/执行双超时监控 + 失败复用实例重发 | `TaskTimeoutMonitor`、`publishTaskWithRetryAndDelay` | 基础不错，保留并叠加（作为兜底层） |
| 心跳：Worker 2s 上报 -> Redis TTL 键，调度端 30s 扫描剔除离线 | `WorkerHeartbeatCheckTask` | 可用，但 Worker 宕机后其 running 任务只能等无心跳超时被动回收，无主动转移 |

### 0.2 与 xxl-job 的能力差距（即本次要补的能力）

调度中心集群高可用（xxl 用 `xxl_job_lock` 行锁）、秒级触发（时间轮 + `trigger_next_time` 落库）、执行器路由策略（10 种）、任务分片广播、阻塞处理策略、故障转移。全项目无 Redisson/锁/next_trigger_time（已 grep 验证），是一张白纸——好画画。

### 0.3 关键代码证据（诊断依据，可直接引用）

- `scheduler-JAVA/.../task/ScheduledTaskRunnerTask.java`：60s 固定轮询，单机调度
- `scheduler-JAVA/.../task/CronTaskScheduler.java`：`shouldExecuteNow` 60s 窗口判断（无 misfire），`TASK_ACTIVE_CACHE` 30 分钟全量缓存
- `scheduler-JAVA/.../service/TaskProducerService.java`：`TASK_EXECUTE` 盲发，无 Tag 无路由
- `sjdt_worker/app/mq_consumer.py` L87-94：消费端 `targetWorkerId` 过滤 + `return True` ACK（丢任务隐患）
- `sjdt_worker/app/config.py`：所有 Worker 共享 `WORKER_EXECUTOR_GROUP` 消费者组
- 全仓库 grep：无 redisson / tryLock / SETNX / FOR UPDATE / nextTrigger / misfire——分布式防重能力为零

---

## 1. 总体架构：改前 -> 改后

```
【改前】
  @Scheduled(60s) 全量扫描 -> shouldExecuteNow(60s窗口) -> 盲发 TASK_EXECUTE
  Worker(同一消费组, expression="*") -> 消费端过滤 targetWorkerId -> 不匹配也 ACK  ← Bug
  负载数据躺在 Redis -> 无人使用；requiredTags -> 死字段；无分片；无阻塞策略

【改后】
  ① 5s 到期扫描(status+next_trigger_time 索引) -> ② Redis 锁(抢轮次) -> ③ CAS 更新
  next_trigger_time(防重复触发) -> ④ misfire 判定 -> ⑤ 阻塞策略 -> ⑥ 路由策略选 Worker
  -> ⑦ MQ Tag=workerId 定向投递(Worker 独立消费组+expression=workerId) -> ⑧ 分片:
  N 条定向消息(shardIndex/shardTotal) -> ⑨ Worker SETNX 抢占幂等执行 -> ⑩ 结果
  条件更新(状态机 CAS) -> ⑪ 失败/超时 -> 延迟重试(重路由) -> ⑫ Worker 离线 ->
  在跑任务主动转移
```

三个根本转变（对应 0.1 的三类缺陷）：

| 维度 | 现状 | 目标 |
|---|---|---|
| 怎么判断"现在该触发" | 扫描时猜：60s 全表扫 + 窗口判断 | 落库预计算：`next_trigger_time` 走索引驱动，触发前 CAS 抢占 |
| 怎么决定"任务给谁跑" | 不决定：盲发 topic，谁分到队列谁跑 | 调度端决策：路由策略选 Worker，Tag 定向投递 |
| 怎么保证"状态不乱" | 事后覆盖：`insertOrUpdate`，迟到结果覆盖新状态 | 条件更新状态机 + Worker 端 SETNX 执行权声明 |

外加横跨三者的一条：**调度节点从单点变集群**（锁 + CAS，无单点且不重复）。

**与 xxl-job 的一个刻意差异（面试必问）**：xxl-job 派发用「调度中心 -> 执行器 HTTP 直连」，因为它不依赖 MQ。你的系统 MQ 已是核心链路（重试依赖延迟消息、结果回流依赖 MQ），所以选 **MQ 定向投递** 而非 HTTP 直连，代价是路由结果只能靠 Tag 表达、无法拿到同步派发 ACK，换来的是队列持久化 + 原生重投递 + 削峰。这个 tradeoff 要能讲清楚。

---

## 2. 阶段一：调度内核高可用（P0，3~4 天）

**动手顺序**：DDL（含存量回填）→ 触发主流程改造（到期扫描 + CAS + misfire）→ `SchedulerLock` → 删除旧缓存逻辑 → 验证。

### 2.1 DDL

```sql
ALTER TABLE tasks
    ADD COLUMN next_trigger_time DATETIME(3) NULL COMMENT '下次理论触发时间',
    ADD COLUMN last_trigger_time DATETIME(3) NULL COMMENT '上次实际触发时间',
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

- `next_trigger_time` 用毫秒精度，触发延迟要统计到 ms。
- `idx_due` 让扫描变成索引命中的到期查询，替代「全量 active 任务 + 30 分钟 Redis 缓存」方案（该缓存整体删除：到期驱动扫描天然只碰即将触发的行）。
- 一次性加齐后续阶段要用的列（`route_strategy`/`block_strategy`/`shard_total`），避免反复改表。
- **存量回填**：启动时 `ApplicationRunner` 对 `next_trigger_time IS NULL AND task_type='cron' AND status='active'` 的任务用 cronutils 算一次补齐，幂等可重跑。

### 2.2 分布式锁（新文件 `common/SchedulerLock.java`）

```java
@Component
public class SchedulerLock {
    private static final String LOCK_KEY = "scheduler:lock:scan";
    private static final String UNLOCK_LUA = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else return 0 end""";
    private final String token = UUID.randomUUID().toString(); // 实例级标识
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public boolean tryLock(Duration ttl) {
        return Boolean.TRUE.equals(
            redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, ttl));
    }
    public void unlock() {
        redisTemplate.execute(unlockScript, List.of(LOCK_KEY), token); // Lua 校验防误删他人锁
    }
}
```

要点（都是面试考点）：
- **value 放实例 token + Lua 校验删除**：防止实例 A 超时后锁已易主、A 的 finally 又把 B 的锁删掉。
- **TTL 略小于扫描周期**（5s 扫描配 4s TTL），宁可偶尔双扫也不能因死锁永久停摆——正确性由下一层的 CAS 兜底。
- Redisson `RLock`（看门狗自动续期）是升级路径，先用原生实现把原理踩一遍。

### 2.3 触发主流程（改 `task/CronTaskScheduler.java` + `ScheduledTaskRunnerTask.java`）

```java
// ScheduledTaskRunnerTask: fixedRate 60000 -> 5000
@Scheduled(fixedRate = 5000)
public void run() {
    if (!lock.tryLock(Duration.ofSeconds(4))) return;   // ① 每轮只有一个实例干活
    try { cronTaskScheduler.scanAndTrigger(); } finally { lock.unlock(); }
}

// CronTaskScheduler 全新方法：
public void scanAndTrigger() {
    LocalDateTime now = LocalDateTime.now();
    List<Task> due = taskService.list(new LambdaQueryWrapper<Task>()
        .eq(Task::getStatus, TaskStatus.active)
        .eq(Task::getTaskType, TaskType.cron)
        .le(Task::getNextTriggerTime, now.plusSeconds(5)));   // ② 索引命中，只查到期行
    for (Task t : due) { try { trigger(t, now); } catch (Exception e) { log... } }
}

private void trigger(Task task, LocalDateTime now) {
    LocalDateTime firedAt = task.getNextTriggerTime();
    LocalDateTime next = nextTriggerTime(task.getCronExpr(), firedAt);  // 复用现有 cronutils 解析缓存

    // ③ misfire：理论触发点已落后超过 5 分钟 -> 放弃补跑本拍，直接对齐未来
    if (firedAt.isBefore(now.minusMinutes(5))) {
        next = nextTriggerTime(task.getCronExpr(), now);
        log.warn("misfire: 任务{}落后{}，跳过补跑对齐至{}", task.getId(), ...);
    }

    // ④ CAS：UPDATE tasks SET next_trigger_time=?, last_trigger_time=?
    //        WHERE id=? AND next_trigger_time=?(旧值) AND status='active'
    int won = taskService.update(new LambdaUpdateWrapper<Task>()
        .eq(Task::getId, task.getId())
        .eq(Task::getNextTriggerTime, firedAt)          // 关键：乐观锁条件
        .eq(Task::getStatus, TaskStatus.active)
        .set(Task::getNextTriggerTime, next)
        .set(Task::getLastTriggerTime, now));
    if (won == 0) return;                                // 别的实例已触发 -> 直接放弃

    dispatch(task, now);                                 // ⑤ 交给阶段二的路由派发
}
```

**双层防重的分工（面试核心话术）**：锁解决「吞吐」（避免 N 个实例空转竞争），CAS 解决「正确性」（就算锁全失效、双实例同时扫，同一行的 `next_trigger_time` 旧值条件只允许一个 UPDATE 成功）。xxl-job 用的是 DB 行锁（`for update` 抢 `xxl_job_lock` 表），原理等价：都把「触发权」收敛到一次原子数据库操作上。

**删除项**：`shouldExecuteNow()` 的 60s 窗口判断、`TASK_ACTIVE_CACHE` 30 分钟缓存及 `@PostConstruct` 清缓存逻辑——被到期驱动 + 索引查询整体替代。"为什么删"也要会讲：旧算法靠「最近理论触发点落在窗口内」判断，重启超 60s 即静默漏触发，且无补偿。

### 2.4 验证

1. 单实例：建 cron `*/1 * * * *`（每分钟）任务跑 10 分钟 -> `SELECT COUNT(*)` 应恰为 10，`trigger_latency_ms` P99 < 2s。
2. 双实例：同库起两个 scheduler 进程跑 10 分钟 -> 仍 10 条、无重复 run（重复触发数=0）。
3. 宕机切换：跑批期间 `kill -9` 实例 A -> 实例 B 下一轮接走，无漏拍（misfire 对齐兜底）。
4. 漏拍回归：把任务 `next_trigger_time` 手工改成 10 分钟前 -> 触发一次对齐日志，不雪崩补跑。

---

## 3. 阶段二：路由策略 + 定向投递修复（P1，3~4 天）

### 3.1 Worker 在线快照（新文件 `route/WorkerRegistry.java`）

数据源全部现成：心跳键 `scheduler:worker:heartbeat:{id}`（TTL 判活）+ 负载 Hash `scheduler:worker:info:{id}`（cpu/mem/runningTasks）。

```java
@Component
public class WorkerRegistry {
    public List<WorkerSnapshot> onlineWorkers() {
        // SCAN（不要用 KEYS *）匹配 heartbeat 前缀 -> 有键即在线
        // 对每个 id HGETALL worker:info:{id} -> 组装 WorkerSnapshot
        // 解析失败的字段给默认值（cpu=0, runningTasks=Integer.MAX_VALUE 保守处理）
    }
    public List<WorkerSnapshot> filterByTags(List<WorkerSnapshot> ws, Set<String> required) {
        // requiredTags ⊆ worker.tags；标签数据已有表 worker_tags（TAG_MATCH 策略用）
    }
}
```

### 3.2 路由策略 SPI（新包 `route/strategy/`）

```java
public interface RouteStrategy {
    String name();                       // 存 tasks.route_strategy
    WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates);
}
```

| 实现类 | 核心逻辑 | 代码要点 |
|---|---|---|
| `RoundRobinStrategy` | 轮询 | Redis `INCR scheduler:route:rr` 计数器取模，多实例共享游标 |
| `RandomStrategy` | 随机 | `ThreadLocalRandom` |
| `ConsistentHashStrategy` | 一致性哈希 | `TreeMap<Long,Worker>` 虚拟节点 ×160，`hash(taskId)` 环上顺时针找；**Worker 集合变更时重建环**（每次调用重建即可，百级节点开销可忽略） |
| `LeastLoadStrategy` | 最少负载 | `score = 0.4*cpu + 0.3*mem + 0.3*100*running/maxConcurrent`，选最小；比 xxl 的 LFU（只数触发频次）更贴近真实负载 |
| `FailoverStrategy` | 故障转移 | 遍历候选（按心跳新鲜度排序），逐个二次探活（`EXISTS heartbeat` key），选第一个活的 |
| `TagMatchStrategy` | 标签匹配 | `filterByTags` 后再走 LEAST_LOAD，无匹配 -> park |

注册中心：`RouteStrategyRouter`（`Map<String,RouteStrategy>`，Spring 注入所有实现自动装配）。任务更新/创建入口透传 `route_strategy` 字段（`TaskCreateDTO`/`TaskUpdateDTO`/`TaskController` 各加一个字段）。

### 3.3 派发与「定向丢任务 Bug」修复（本阶段最大价值）

**Bug 复述（要写进 commit message）**：RocketMQ 集群模式下同组一条消息只投给一个消费者；`mq_consumer.py` L87-94 非目标 Worker 收到定向任务直接 `return True`（ACK），消息对全组已消费 -> 任务静默丢失，靠 120s 派发超时反复兜底。

**修复设计——独立消费组 + Broker 侧 Tag 过滤**：

调度侧（改 `TaskProducerService`）：
```java
public void sendTask(TaskExecuteMessage msg, String workerId) {
    // 目的地 "topic:tag" -- RocketMQTemplate 的 Tag 语法
    rocketMQTemplate.syncSend("TASK_EXECUTE:" + workerId, MessageBuilder.withPayload(msg).build(), 3000);
}
```

Worker 侧（改 `app/mq_consumer.py`）：
```python
# 1. 消费组改为每 Worker 独立：组内只有一个实例，规避"同组订阅表达式不一致"经典坑
self.task_consumer = PushConsumer(f"{Config.MQ_CONSUMER_GROUP}_{Config.WORKER_ID}")
# 2. 订阅表达式从 "*" 改为自己的 workerId：Broker 侧过滤，别人的消息根本不会推给我
self.task_consumer.subscribe(
    Config.MQ_TASK_TOPIC,
    self._message_listener,
    expression=Config.WORKER_ID
)
# 3. 删除消费端的 targetWorkerId 过滤分支（L87-94）—— 路由权收归调度侧
```

**为什么必须改组名（关键设计决策，面试深挖点）**：RocketMQ 要求同一消费组内所有实例的订阅表达式一致；若 N 个 Worker 共用一组、各自 expression=自己 id，rebalance 时订阅表互相覆盖，行为未定义。改成「一 Worker 一组」后：组内单实例无 rebalance 问题；Broker 按 Tag 过滤，非本 Worker 的消息只推进 offset 不投递（零浪费）；消息 Tag 与订阅精确匹配，定向语义由 Broker 保证。

**前置条件已验证**：Worker 按 host/IP 复用身份（`getByHostOrIp`），workerId 持久化在本地配置，重启不变 -> Tag/组名稳定。注意 `MQConsumer` 的构造必须发生在注册完成之后（`main.py` 里确认初始化顺序）。

**兜底链路**：
- 路由无可用 Worker（全离线/标签无匹配）-> 不发消息，TaskRun 置 `pending`，本轮告警，下一轮扫描重试（park 的 run 由 120s 派发超时监控回收为 failed——沿用现有 `TaskTimeoutMonitor` 逻辑）。
- `targetWorker`（用户钉死指定）语义保留：路由阶段若 `task.targetWorker != null` 直接选它（在线校验失败 -> park 告警，不再盲发）。

### 3.4 验证

1. 3 个 Worker + 100 个定向任务 -> 修复前（抽查确认丢任务日志），修复后 100% 由目标 Worker 执行（worker 日志 `定向任务匹配` 计数核对）。
2. 1000 个自动路由任务均匀分布（各 Worker 执行数方差 < 15%，LEAST_LOAD 下观察 3 个负载不同进程的倾斜）。
3. 一致性哈希：杀掉 1/3 Worker，统计 `taskId -> workerId` 映射变化比例（应 ≈ 1/3 迁移，其余不动）。

---

## 4. 阶段三：任务分片（P1，3 天）

### 4.1 消息与派发（shardTotal 固定，Worker 不足则一台多片）

`TaskExecuteMessage` 增加字段：`shardIndex / shardTotal / shardBatchId`。触发时（`shard_total > 0`）：

```java
int shardTotal = task.getShardTotal();              // 固定值：hash 分区永不漂移（决策 #2）
List<WorkerSnapshot> online = registry.onlineWorkers();
if (online.isEmpty()) { parkAndAlert(task); return; }

// 片 -> Worker 映射：shard i 发给 online.get(i % online.size())
// 片数可大于 Worker 数 -> 一台跑多片是常态（还利于均衡）；
// 绝不"动态缩片"：shardTotal 一变，hash(数据)%total 的分区全变，违背不重不漏
String batchId = UUID.randomUUID().toString();
for (int i = 0; i < shardTotal; i++) {
    TaskRun run = createShardRun(task, i, shardTotal, batchId);   // task_runs 每片一行
    producer.sendTask(messageWithShard(task, i, shardTotal, batchId),
                      online.get(i % online.size()).id());
}
```

**与 xxl-job 的差异（面试点）**：xxl 用「广播触发，执行器按自己在注册表中的序号自认分片」；我们用「N 条 Tag 定向消息」——因为复用了阶段二的定向投递基础设施，且天然支持**单片失败单片重试**（xxl 的广播重试是整体重发）。代价是 N 条消息流量，规模级 tradeoff 要能讲。

### 4.2 Worker 端注入（改 3 个 handler + `task_service.py`）

```python
# process_task 组装子进程 env 时追加（python/java/go 三个 handler 同样处理）：
env["SJDT_SHARD_INDEX"] = str(task.shardIndex)
env["SJDT_SHARD_TOTAL"] = str(task.shardTotal)
# 另外注入 SJDT_SHARD_BATCH_ID，脚本可自证批次
```

业务脚本侧约定（README 写明）：`hash(数据主键) % SJDT_SHARD_TOTAL == SJDT_SHARD_INDEX` 的行归本片处理。

### 4.3 分片结算（新 `service/ShardSettlementService.java`）

每片结果到达时（`TaskResultConsumer` 钩子）：

```java
long done = taskRunMapper.selectCount(new LambdaQueryWrapper<TaskRun>()
    .eq(TaskRun::getShardBatchId, batchId)
    .in(TaskRun::getStatus, TaskRunStatus.success, failed, timeout));
if (done == shardTotal) {
    // 全部终态 -> 汇总：任一片 failed -> 批次 failed；否则批次 success
    // 批次汇总行可用 batchId 虚拟行或直接更新所有片 + 发 Dashboard 事件
}
```

- **非分片任务即 N=1 特例**（shardIndex=0、shardTotal=1、batchId=runId），结算走同一条代码路径，无需两套逻辑（决策 #3）。
- 单片失败重试：沿用现有 `publishTaskWithRetryAndDelay`，但重试消息只带该片的 `shardIndex`（路由阶段重新选 Worker）。**分片不迁移**（数据集 -> 片的映射由 hash 固定，重试不换片，保证不重不漏处理同一段数据）。

### 4.4 验证 + 造数字

Demo 脚本（写进 `examples/shard_demo.py`）：生成 100 万行 CSV（每行一个 UUID 主键），按片过滤统计行数：
- 1 分片 vs 5 分片 vs 10 分片耗时 -> 吞吐倍数（预期 6~8x）
- 片间行数互斥校验（Σ各片行数 == 总行数，两两交集为空）-> 证明不重不漏
- 杀 1 片的 Worker -> 该片重试成功，其他片不受影响

---

## 5. 阶段四：阻塞策略 + 主动故障转移（P2，2~3 天）

### 5.1 阻塞策略（触发时判定，改 `CronTaskScheduler.trigger()` 的 `dispatch` 前）

```java
TaskRun running = taskRunMapper.selectOne(new LambdaQueryWrapper<TaskRun>()
    .eq(TaskRun::getTaskId, task.getId())
    .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending)
    .orderByDesc(TaskRun::getCreateTime).last("LIMIT 1"));
if (running != null) {
    switch (task.getBlockStrategy()) {
        case DISCARD_LATER -> { logRunSkipped(task); return; }          // 最常用：报表类
        case COVER_EARLY  -> taskProducerService.sendTaskControl(stopMsgFor(running)); // 杀旧派新（复用广播控制 topic）
        case SERIAL       -> createQueuedRun(task);                       // 置 queued，等上一轮终态
    }
}
```

`SERIAL` 的推进器：`QueuedRunDispatcher`（并入 5s 扫描循环）：捞 `queued` 状态的 run，同 taskId 无 running 兄弟时转 `pending` 并派发。`TaskRunStatus` 枚举增加 `queued`/`skipped`。

### 5.2 Worker 离线主动转移（改 `WorkerHeartbeatCheckTask.handleWorkerOffline()`）

- 存活检测从 30s 拆为 **10s 一轮**（DB 信息同步维持 30s 不变），故障发现延迟从最差 30s 降到 10s。
- 现有行为只有「标记离线 + 清 targetWorker」，补主动转移（决策 #4）：

```java
// ① 离线 Worker 名下 running/pending 的孤儿 run -> 条件更新判终态
//    failed("worker offline, failover")；不复用旧 runId，不与旧实例可能残留的 SETNX 执行权纠缠
List<TaskRun> orphans = taskRunMapper.selectList(...
    .eq(TaskRun::getWorkerId, offlineWorkerId)
    .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending));
for (TaskRun run : orphans) {
    markFailed(run, "worker offline, failover");   // 条件更新，防与迟到结果竞态
    TaskRun newRun = createRetryRun(run);          // ② 新建 run，沿用任务自身重试预算
    dispatch(newRun);                              // ③ 重发 -> 路由策略重新选在线 Worker
}
```

- 配合现有无心跳超时监控兜底（双保险：主动转移 10s 级，超时监控兜漏网）。
- 语义 at-least-once：旧 Worker 若假死复活，其迟到结果会被「条件更新」拒绝（旧 run 已终态，更新 0 行丢弃），不会污染新 run；MQ 层面重复投递由 SETNX 兜底。

### 5.3 幂等补全

- **Worker 抢占**（改 `task_service.py`）：执行前 `SET task:run:claim:{taskRunId} workerId NX EX {timeout+60}`；已存在 -> 直接 ACK 不执行（MQ 重投/重复消息防线）。
- **结果条件更新**（改 `TaskResultConsumer`）：`insertOrUpdate` -> `UPDATE ... WHERE id=? AND status IN ('pending','running')`；返回 0 行说明是迟到结果（超时重试已换实例），丢弃并 debug 日志——修掉「旧结果覆盖新状态」的竞态。

---

## 6. 阶段五：可观测、压测与工程化（P2，3~4 天）

1. **指标**：`task_runs.trigger_latency_ms` 已埋点 -> `DashboardController` 增加：触发延迟 P50/P99、派发成功率、各路由策略命中分布、分片批次视图、丢弃/超时计数。调度器实例自注册 `scheduler:instances`（ZSet，score=心跳时间戳）-> Dashboard 可见调度集群视图。Grafana 可选（有 docker-compose 后接 Prometheus 顺理成章，不强求）。
2. **压测**（`benchmark/submit_burst.py`）：双调度实例 + 10 Worker，1000 任务/分钟 × 10 分钟。产出表：

| 指标 | 目标 | 改造前 |
|---|---|---|
| 重复触发 | 0 | 双实例必重复 |
| 触发延迟 P99 | < 2s | 最差 ~60s |
| 定向投递成功率 | 100% | 存在静默丢失 |
| 10 分片吞吐 | ≥ 6x 单片 | - |

3. **工程化**：`docker-compose.yml`（MySQL/Redis/RocketMQ+broker/MinIO/scheduler ×2/worker ×3）替代 `dev-start.txt`；README 加 mermaid 架构图 + 压测数据表 + 分片示例；Git 历史按阶段整理（每阶段一个 merge，commit message 讲清动机，Bug 修复单独成 commit）。

---

## 7. 全量改动清单（对照现有文件）

| 文件 | 改动 | 阶段 |
|---|---|---|
| `ScheduledTaskRunnerTask.java` | 60s->5s + 锁 | 1 |
| `CronTaskScheduler.java` | 删 `shouldExecuteNow`/缓存 -> 到期扫描 + CAS + misfire + 阻塞判定 + 分片触发 | 1/3/4 |
| `common/RedisKey.java` | + `LOCK_SCAN`、`ROUTE_RR`、`RUN_CLAIM`、`SCHEDULER_INSTANCES` 前缀 | 1/2/4/5 |
| 新 `common/SchedulerLock.java` | setIfAbsent + Lua 释放 | 1 |
| 新 `route/WorkerRegistry.java`、`route/strategy/*`（6 类 + Router） | 路由 SPI | 2 |
| `TaskProducerService.java` | + `sendTask(msg, workerId)` Tag 定向 | 2 |
| `Task`/`TaskCreateDTO`/`TaskUpdateDTO`/`TaskController` | + `nextTriggerTime`/`routeStrategy`/`blockStrategy`/`shardTotal` 透传 | 1/2/3 |
| 新 `service/ShardSettlementService.java`、`QueuedRunDispatcher.java` | 分片结算 / 串行推进 | 3/4 |
| `TaskRun`/`TaskExecuteMessage` | + shard/latency 字段 | 3 |
| `TaskResultConsumer.java` | 条件更新状态机 | 4 |
| `WorkerHeartbeatCheckTask.java` | 存活检测 30s->10s + 离线孤儿 run 主动转移 | 4 |
| `TaskTimeoutMonitor.java` | 保留为兜底（语义不变） | - |
| `sjdt_worker/app/mq_consumer.py` | 独立消费组 + expression=workerId + 删消费端过滤 | 2 |
| `sjdt_worker/app/service/task_service.py` | SETNX 抢占 | 4 |
| `sjdt_worker/app/service/task_handler/*.py` ×3 | 分片环境变量注入 | 3 |
| `TaskRunStatus.java` | + `queued`/`skipped` | 4 |
| 新 `benchmark/`、`docker-compose.yml`、`examples/shard_demo.py` | 压测/部署/演示 | 5 |
| 删除 | `TASK_ACTIVE_CACHE` 相关（`RedisKey`、`CronTaskScheduler`、`TaskServiceImpl` 两处清缓存） | 1 |

---

## 8. 实施顺序与排期

```
W1 前半: 阶段一（锁+CAS+misfire）        -- 不依赖别人，先把"不重不漏"立住
W1 后半: 阶段二（注册表+路由+Tag 修复）   -- 依赖阶段一的 dispatch 钩子位
W2 前半: 阶段三（分片）                   -- 复用阶段二定向投递
W2 后半: 阶段四（阻塞+转移+幂等）         -- 依赖阶段二的"路由权在调度侧"
W3:      阶段五（指标+压测+compose）      -- 全部能力就绪后测
```

- 依赖链：1 -> 2 -> 3（依赖 2 的定向投递）-> 4 -> 5。
- 每阶段完成即打 tag（`v1-ha` / `v2-routing` / `v3-sharding` / `v4-reliability` / `v5-benchmark`），压测脚本按 tag 对比改造前后。
- **动手前先跑一轮基线压测存档**——"改造前"的重复触发/延迟/丢任务数据必须真实存在，否则对比数字无从谈起。

---

## 9. 明确不做的（范围控制）

- 不做 xxl 的 GLUE 在线编辑、执行器自动注册 UI、邮件告警平台——对面试叙事无增益。
- 不做调度中心选主（Redis 主从/哨兵、Quartz 集群那种）：锁+CAS 已满足"无单点 + 不重复"，讲清楚"为什么不需要更强的一致性"比堆复杂度加分。
- 时间轮（Redis ZSet + Lua 弹出）只作为**面试延伸**准备，不落地：5s 扫描 + 索引在万级任务内延迟已达秒级，ZSet 方案收益 < 复杂度——这个判断本身就是要讲的设计取舍。

---

## 10. 面试深挖问答（必背 13 问）

1. 调度集群为什么需要分布式锁？**锁过期了还会重复触发吗？** -> 不会，DB 的 CAS（`WHERE next_trigger_time=旧值`）是第二道防线，锁只是减少竞争。
2. 为什么用 DB `next_trigger_time` 而不是纯内存时间轮？Redis ZSet 时间轮呢？ -> 内存时间轮重启丢任务；ZSet 跨实例共享 + 可恢复 + Lua 原子弹出，但 5s 扫描 + 索引已够用（万级任务秒级延迟），ZSet 收益 < 复杂度，列入"明确不做的"——取舍本身是加分项。
3. **定向任务之前为什么会丢？怎么修的？** -> RocketMQ 集群模式一条消息只投组内一个消费者，消费端"不匹配就 ACK"等于吞掉；改为生产端 Tag 定向 + 消费端独立消费组 + 按 Tag 订阅。
4. **定向投递为什么必须"一 Worker 一消费组"？** -> RocketMQ 要求同组订阅表达式一致；共用一组、各自订阅自己的 id，rebalance 时订阅表互相覆盖、行为未定义。独立组后组内单实例无 rebalance 问题，Broker 按 Tag 过滤零浪费。
5. 一致性哈希解决什么问题？虚拟节点的作用？Worker 扩容后任务怎么迁移？ -> 数据倾斜与最小迁移；虚拟节点打散热点；扩容只迁移 ~1/N，其余不动（压测可证）。
6. 如何保证任务**不重不漏**？ -> 调度侧 CAS 防重触发、消费侧 SETNX 防重消费、状态机条件更新防重复结算、misfire 补偿防漏；MQ 本身 at-least-once，靠幂等收口。
7. 分片任务某片失败怎么办？怎么保证不漏数据？ -> hash 稳定分片（shardTotal 固定，绝不动态缩片）+ 单片独立重试（重试不换片）+ 全片终态才结算。
8. **分片为什么发 N 条定向消息，而 xxl-job 用广播？** -> 复用定向投递基础设施，天然支持单片重试；xxl 广播只能整批重发、Worker 集合变化时分片序号漂移。代价是 N 条消息流量——规模级 tradeoff。
9. Worker 宕机时它上面 running 的任务怎么办？ -> 心跳 TTL 剔除（10s 检测）-> 孤儿 run 判终态 + 新建 run 重路由转移（秒级生效）+ 无心跳超时监控兜底（双保险）；at-least-once，重复由 SETNX 兜底，迟到结果被条件更新拒绝。
10. 为什么选 RocketMQ 不选 Kafka？ -> 延迟消息原生支持（重试链路依赖）、Tag 过滤（定向投递依赖）、中文生态；Kafka 延迟消息要自建。
11. 阻塞策略三种的适用场景？ -> SERIAL：上下游有顺序依赖的链路任务；DISCARD_LATER：报表类（默认，本轮丢弃）；COVER_EARLY：以最新数据为准的同步类。
12. 任务量涨 100 倍哪里先瓶颈？ -> 扫描锁串行 -> 按任务 hash 分桶多把锁分片扫描；task_run 写热点 -> 批量/异步落库。
13. **你的派发用 MQ，xxl-job 用 HTTP 直连，为什么？** -> xxl 不依赖 MQ 所以直连；本系统 MQ 是核心链路（重试=延迟消息、结果回流=MQ），保留 MQ 换来队列持久化 + 原生重投递 + 削峰，代价是拿不到同步派发 ACK。设计取舍要能讲清。
