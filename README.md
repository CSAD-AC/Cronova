# SJDT 任务引擎

SJDT 任务引擎是一个分布式任务调度系统，支持两种任务类型：**脚本任务**（Worker 执行）和 **HTTP 回调任务**（调度器直连业务系统）。

## 仓库结构

| 目录 | 说明 |
|---|---|
| `scheduler-JAVA/` | 调度中心（Spring Boot）：任务管理、到期触发、路由派发、分片、结果消费、Worker 管理 |
| `worker-Python/` | Worker 执行器（Python）：脚本任务接收、执行、状态汇报、日志管理 |
| `worker-JAVA/` | Worker 执行器（Java）：功能对等 Python 执行器，可独立部署并行共存 |
| `frontend/` | 前端（Vue 3 + Element Plus） |
| `examples/` | 各语言回调任务模板示例（java / python / go） |

---

## 简介

**SJDT任务引擎**是一个分布式任务调度系统，支持两种任务类型：**脚本任务**和**HTTP回调任务**。

### 核心特性

- **分布式架构**：调度中心多实例互斥调度，Worker 多执行器部署，水平扩展
- **定时调度**：基于Cron表达式的精确调度（5s 扫描周期）
- **双任务模式**：脚本任务 + HTTP回调任务
- **路由策略**：轮询 / 最少负载 / Tag 定向，MQ Tag 硬定向投递
- **任务分片**：大数据量任务按分片并行执行，批次聚合结算
- **阻塞策略**：丢弃后续 / 覆盖先前 / 串行排队
- **任务控制**：暂停、恢复、停止、强制终止
- **实时监控**：心跳检测、状态实时上报、触发延迟/路由分布等调度指标
- **安全认证**：AK/SK签名认证

---

## 任务类型

本系统支持两种任务执行模式：

### 1. 脚本任务 (Script Task)

**定义**：通过Worker执行Python/Java/Go等脚本文件的任务。

**执行流程**：
```
用户创建任务 ──→ 调度器触发 ──→ MQ下发 ──→ Worker接收 ──→ 执行脚本 ──→ 结果上报
```

**特点**：
- 需要部署Worker执行器
- 支持多种脚本语言
- 完整的进程控制能力（暂停/恢复/停止）
- 详细的执行日志收集

### 2. HTTP回调任务 (Callback Task)

**定义**：调度器通过HTTP请求调用业务系统接口的任务。

**执行流程**：
```
用户创建任务 ──→ 调度器触发 ──→ HTTP请求 ──→ 业务系统处理 ──→ 响应回调 ──→ 状态更新
```

**特点**：
- 无需部署Worker
- 适合调用现有业务系统接口
- 完整的请求/响应日志记录

---

## 模块划分

### 1. 调度器 (Scheduler)

**职责**：任务中枢管理，负责所有任务的创建、调度、监控和结果处理。

| 子模块 | 功能 |
|--------|------|
| **任务管理** | 任务的CRUD、启用/禁用、发布 |
| **定时调度** | 基于Cron表达式的定时任务触发（分布式锁互斥，多实例不重复触发） |
| **脚本任务处理** | 通过MQ下发脚本任务到Worker（按目标 WorkerID 打 Tag 硬定向） |
| **路由策略** | 轮询 / 最少负载 / Tag 定向 |
| **分片派发** | 大任务按 shardTotal 拆片，轮转分布到多 Worker 并行执行，批次聚合结算 |
| **阻塞策略** | 丢弃后续 / 覆盖先前 / 串行排队（仅非分片任务） |
| **回调任务处理** | 直接发起HTTP请求调用业务系统 |
| **结果消费** | 消费任务执行结果，状态单向迁移（迟到结果丢弃） |
| **超时监控** | 派发超时 / 执行超时兜底与重试 |
| **排队推进** | 上一轮终态后自动将排队 run 推进派发 |
| **Worker管理** | 注册、心跳检测、状态监控、离线任务转移 |

**技术栈**：Spring Boot 3.3.6 + MyBatis-Plus + RocketMQ + Redis + MinIO

---

### 2. 执行器 (Worker)

**职责**：仅负责**脚本任务**的接收、执行、状态汇报和日志管理。

| 子模块 | 功能 |
|--------|------|
| **注册服务** | 启动时向调度器注册，获取AK/SK |
| **消息消费** | 监听MQ，接收脚本任务执行指令 |
| **任务执行** | 下载脚本，创建进程执行 |
| **状态汇报** | 定期上报任务执行状态和系统负载 |
| **日志管理** | 实时收集日志，完成后上传MinIO |
| **心跳服务** | 定期发送心跳，保持在线状态 |

**技术栈**：Python 3.8+ + RocketMQ Client + MinIO Client

---

### 3. 前端界面 (Vue)

**职责**：提供可视化任务管理界面。

| 页面 | 功能 |
|------|------|
| **仪表盘** | 系统运行概览、关键指标展示 |
| **任务管理** | 创建、配置、启停任务 |
| **运行记录** | 查看任务执行历史和日志 |
| **脚本管理** | 上传和管理执行脚本 |
| **Worker管理** | 查看执行器状态和负载 |
| **回调配置** | 配置HTTP回调地址和认证信息 |

**技术栈**：Vue 3 + Element Plus + TypeScript + Pinia

---

### 4. 存储层

| 组件 | 用途 | 存储内容 |
|------|------|----------|
| **MySQL** | 持久化存储 | 用户、角色、任务定义、运行记录、脚本、标签、回调配置 |
| **Redis** | 缓存与协调 | Worker心跳/负载、分布式锁、轮询路由游标、调度实例注册、分片结算幂等、脚本/AKSK缓存 |
| **MinIO** | 对象存储 | 脚本文件、任务日志、HTTP回调记录 |

---

## 业务流程

### 脚本任务流程

```
步骤1: 任务创建与配置
───────────────────────────────────────────────────────────────────────
用户/前端 ──→ POST /api/tasks (executeType=script) ──→ MySQL持久化
                                                              │
                                                              └── 加载到内存(CronTask)

步骤2: 任务触发
───────────────────────────────────────────────────────────────────────
CronTask ──→ 满足触发条件 ──→ 构建TaskExecuteMessage ──→ 发送到RocketMQ(TASK_EXECUTE)

步骤3: Worker执行
───────────────────────────────────────────────────────────────────────
Worker(MQ消费者) ──→ 接收消息 ──→ 验证Worker匹配 ──→ 从MinIO下载脚本 ──→ 创建进程执行
                                                                         │
                                                                         ├── 超时监控
                                                                         ├── 实时日志收集
                                                                         └── 定期状态上报

步骤4: 结果处理
───────────────────────────────────────────────────────────────────────
Worker ──→ 执行完成 ──→ 上传日志到MinIO ──→ 发送结果到RocketMQ(TASK_RESULT)
                                                          │
                                                          ↓
                                                   调度器消费结果 ──→ 更新数据库 ──→ 通知前端
```

### HTTP回调任务流程

```
步骤1: 任务创建与配置
───────────────────────────────────────────────────────────────────────
用户/前端 ──→ POST /api/tasks (executeType=callback) ──→ MySQL持久化
                                                               │
                                                               └── 同时创建回调配置 (callback_job_configs)
                                                                               │
                                                                               └── 保存回调URL、认证信息、超时配置

步骤2: 任务触发
───────────────────────────────────────────────────────────────────────
CronTask ──→ 满足触发条件 ──→ 构建回调请求 ──→ 发起HTTP请求
                                                    │
                                                    ├── 验证签名/认证
                                                    ├── 发送请求体
                                                    └── 记录请求日志

步骤3: 业务系统处理
───────────────────────────────────────────────────────────────────────
业务系统 ──→ 接收请求 ──→ 执行业务逻辑 ──→ 返回处理结果

步骤4: 结果处理
───────────────────────────────────────────────────────────────────────
调度器 ──→ 接收响应 ──→ 记录响应日志 ──→ 更新数据库状态 ──→ 通知前端
                     │
                     ├── 成功 (status=2xx) ──→ 更新为SUCCESS
                     ├── 失败 (status=4xx/5xx) ──→ 更新为FAILED
                     └── 超时 ──→ 更新为TIMEOUT
```

---

## 任务类型对比

| 特性 | 脚本任务 | HTTP回调任务 |
|------|----------|-------------|
| **执行方式** | Worker执行脚本 | 调度器发起HTTP请求 |
| **依赖组件** | 需要部署Worker | 无需部署Worker |
| **脚本支持** | Python/Java/Go等 | 不适用 |
| **进程控制** | 支持暂停/恢复/停止 | 不适用 |
| **执行日志** | 实时收集、上传MinIO | 请求/响应日志记录 |
| **超时控制** | 可配置、超时终止进程 | 可配置、超时中断请求 |
| **重试机制** | 支持自动重试 | 支持自动重试 |
| **适用场景** | 数据处理、报表生成 | 调用现有业务系统API |
| **监控内容** | CPU/内存/进程状态 | 请求状态码/响应时间 |

---

## Worker生命周期

```
启动 ──→ 注册 ──→ 正常运行 ──→ 退出
         │          │
         │          ├── 心跳上报 (2秒/次)
         │          ├── 状态上报 (10秒/次)
         │          ├── MQ消费 (TASK_EXECUTE, 独立消费组 + 按 workerId Tag 订阅) ──→ 脚本任务
         │          └── MQ消费 (TASK_CONTROL) [广播] ──→ 控制指令
         │
         └── POST /api/worker/register ──→ 获取AK/SK
```

---

## 任务状态流转

状态迁移是**单向**的：仅 `pending` / `running` 可推进到终态，终态后迟到到达的执行结果会被丢弃（防止旧结果覆盖新状态）。

```
PENDING ──→ RUNNING ──→ SUCCESS / FAILED / TIMEOUT (终态)
   │           │
   │           ├── PAUSE ──→ PAUSED ──→ RESUME ──→ RUNNING
   │           ├── STOP ──→ 结束
   │           └── KILL ──→ 结束
   │
   ├── 触发时上一轮未结束且策略为 SERIAL ──→ QUEUED (排队, 上一轮终态后推进为 PENDING 派发)
   └── 触发时被阻塞策略拦截 ──→ SKIPPED (丢弃/覆盖, 终态)
```

---

## 快速开始

### 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Python | 3.8+ |
| Node.js | 16+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| RocketMQ | 4.9+ |
| MinIO | 最新版 |

### 启动步骤

**1. 数据库初始化**（Flyway 自动迁移）

只需创建空数据库，调度器启动时 Flyway 自动执行迁移（`src/main/resources/db/migration/`）建表并写入种子数据：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS scheduler DEFAULT CHARACTER SET utf8mb4"
cd scheduler-JAVA/
./mvnw spring-boot:run   # 启动时自动迁移
```

开发环境一键重建（会清空数据）：

```bash
mysql -u root -p -e "DROP DATABASE IF EXISTS scheduler; CREATE DATABASE scheduler DEFAULT CHARACTER SET utf8mb4"
```

历史手动脚本（含 `DROP DATABASE` 全量重建）已归档至 `scheduler-JAVA/sql/archive/`。

**2. 启动调度器**

```bash
cd scheduler-JAVA/
./mvnw spring-boot:run
# 默认端口: 8085
# Swagger: http://localhost:8085/swagger-ui.html
```

**3. 启动前端**

```bash
cd frontend/
npm install
npm run dev
# 默认端口: 5173
```

**4. 部署Worker**（仅脚本任务需要）

```bash
cd worker-Python/
python main.py
# 或使用Docker
cd docker/
docker-compose up -d
```

---

## API 概览

### 任务管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/tasks` | POST | 创建任务 |
| `/api/tasks` | GET | 任务列表 |
| `/api/tasks/{id}` | GET | 任务详情 |
| `/api/tasks` | PUT | 更新任务 |
| `/api/tasks/{id}` | DELETE | 删除任务 |
| `/api/tasks/{id}/enable` | PUT | 启用任务 |
| `/api/tasks/{id}/disable` | PUT | 禁用任务 |
| `/api/tasks/{id}/publish` | POST | 发布任务 |
| `/api/tasks/{id}/runs` | GET | 执行历史 |

### 任务运行

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/task-run/list` | GET | 运行实例列表 |
| `/api/task-run/task/{taskId}` | GET | 根据任务ID查询实例 |
| `/api/task-run/{taskRunId}` | GET | 运行详情 |
| `/api/task-run/{taskRunId}/logs` | GET | 运行日志 |
| `/api/task-run/{taskRunId}/log/info` | GET | 日志文件信息 |
| `/api/task-run/{taskRunId}/pause` | POST | 暂停运行 |
| `/api/task-run/{taskRunId}/resume` | POST | 恢复运行 |
| `/api/task-run/{taskRunId}/stop` | POST | 停止运行 |
| `/api/task-run/{taskRunId}/kill` | POST | 强制终止 |
| `/api/task-run/{taskRunId}` | DELETE | 删除运行实例 |

### 仪表盘

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/dashboard/stats` | GET | 总览统计（任务数/成功率/近7天图表/类型分布） |
| `/api/dashboard/metrics` | GET | 调度链路指标：触发延迟 P50/P95/P99、派发情况、路由策略分布、最近分片批次、在线调度实例数（`minutes` 窗口参数） |

### 分片批次

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/shard-batches/{batchId}/logs` | GET | 分片批次日志（按时间排序聚合） |
| `/api/shard-batches/{batchId}/logs/info` | GET | 分片日志文件信息 |

### 回调配置管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/callback-configs` | POST | 创建回调配置 |
| `/api/callback-configs` | GET | 回调配置列表 |
| `/api/callback-configs/{id}` | GET | 回调配置详情 |
| `/api/callback-configs/{id}` | PUT | 更新回调配置 |
| `/api/callback-configs/{id}` | DELETE | 删除回调配置 |

### Worker管理

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/worker/register` | POST | Worker注册 |
| `/api/worker/heartbeat/{workerId}` | POST | 心跳上报 |
| `/api/worker/status/{workerId}` | POST | 状态报告 |
| `/api/worker/validate-aksk` | POST | 验证AKSK |
| `/api/worker/task-result/{workerId}` | POST | 任务结果报告 |
| `/api/worker/task-status/{taskRunId}` | POST | 任务状态报告 |
| `/api/worker/task/publish/{taskId}` | POST | 发布任务 |
| `/api/frontend/worker/list` | GET | Worker列表 |
| `/api/frontend/worker/{id}` | GET | Worker详情 |
| `/api/frontend/worker/{id}/host` | PUT | 更新主机名 |
| `/api/frontend/worker/{id}` | DELETE | 删除Worker |

---

## 配置说明

### 调度器 (application.yml)

```yaml
server:
  port: 8085

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/scheduler
    username: root
    password: changeme

rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: scheduler-producer
  consumer:
    group: scheduler-consumer

minio:
  endpoint: http://localhost:9000
  accessKey: changeme
  secretKey: changeme
  bucket-name: task-engine
```

### Worker (config.py)

```python
# RocketMQ
MQ_NAMESRV_ADDR = "127.0.0.1:9876"
MQ_CONSUMER_GROUP = "WORKER_EXECUTOR_GROUP"
MQ_TASK_TOPIC = "TASK_EXECUTE"
MQ_TASK_RESULT_TOPIC = "TASK_RESULT"
MQ_TASK_CONTROL_TOPIC = "TASK_CONTROL"

# MinIO
MINIO_ENDPOINT = "http://localhost:9000"
MINIO_ACCESS_KEY = "changeme"
MINIO_SECRET_KEY = "changeme"
MINIO_BUCKET_NAME = "task-engine"

# 心跳与状态
HEARTBEAT_INTERVAL = 2
STATUS_REPORT_INTERVAL = 10
```

---

## 安全机制

### AKSK 签名认证

Worker与调度器通信使用AK/SK签名：

```python
signature = HMAC-SHA256(secret_key, timestamp)
```

**请求头**：

| Header | 说明 |
|--------|------|
| `X-Access-Key` | Worker的Access Key |
| `X-Signature` | HMAC-SHA256签名 |
| `X-Timestamp` | Unix时间戳（秒） |

---

## 数据存储

### MinIO 路径规范

```
task-engine bucket/
├── scripts/{uuid}.{ext}          # 脚本文件
├── task-logs/{taskId}/{runId}.log # 任务日志
└── callback/{yyyy/MM}/{id}-{req/res}.json # HTTP回调记录
```

### Redis 缓存

| Key | 类型 | 说明 | 过期 |
|-----|------|------|------|
| `scheduler:worker:heartbeat:{workerId}` | String | Worker 存活状态 | 30s |
| `scheduler:worker:info:{workerId}` | Hash | Worker 负载信息（CPU/内存/运行任务数） | 无 |
| `scheduler:lock:*` | String | 定时任务分布式锁（scan / heartbeat / timeout / queued，`@SchedulerLocked` AOP 统一管理，TTL 按调度周期自动推导） | 自动过期 |
| `scheduler:route:rr` | String | 轮询路由游标（ROUND_ROBIN 共享计数器） | 无 |
| `scheduler:shard:settle:{batchId}` | String | 分片批次结算幂等标记（同批次只结算一次） | 结算后过期 |
| `scheduler:instances` | ZSet | 调度实例注册表（member=实例ID，score=心跳时间戳，30s 内有心跳视为在线） | 死成员定期清理 |

---

## 高可用设计

### 调度器多实例互斥

所有定时任务（任务扫描 / 心跳检查 / 超时监控 / 排队推进）由 `@SchedulerLocked` AOP 分布式锁互斥：

- 多实例部署时，同一时刻只有一个实例执行扫描，**不会重复触发任务**；
- 锁 TTL 按调度周期自动推导（周期 × 80%，cron 型兜底 30s）；
- 启动期校验：漏加锁或注解位置错误的定时任务会在启动时直接报错。

### Worker离线检测与任务转移

```
Worker启动 ──→ 注册 ──→ 正常运行 ──→ 心跳/状态上报
                                 │
                                 └── 心跳超时 ──→ 标记离线
                                                      │
                                                      ↓
                              离线Worker的运行中任务 ──→ 10s 内转移到健康 Worker 继续执行
```

### 阻塞与排队（触发并发控制，仅非分片任务）

| 策略 | 行为 |
|------|------|
| **DISCARD_LATER** | 上一轮未结束时丢弃本次触发（run 记为 `skipped`） |
| **COVER_EARLY** | 上一轮未结束时覆盖先前（旧 run 记为 `skipped`） |
| **SERIAL** | 上一轮未结束时排队（run 记为 `queued`），上一轮终态后自动推进派发 |

### 任务重试策略

| 场景 | 触发条件 | 处理方式 |
|------|----------|----------|
| **失败重试** | Worker返回失败状态 | 创建新实例(New TaskRunId) |
| **超时重试** | Pending超时/Running超时无心跳 | 重用实例(Same TaskRunId) |

---

## 应用场景

### 脚本任务适用

- 数据同步与处理
- 报表生成与发送
- 系统维护与监控
- 缓存清理与更新
- 数据备份与恢复
- 批量数据处理

### HTTP回调任务适用

- 调用现有业务系统API
- 触发第三方服务
- 通知消息推送
- 集成外部系统
