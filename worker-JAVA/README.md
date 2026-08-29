# Java 执行器（worker-JAVA）

任务调度系统的 Java 实现执行器，与调度器（scheduler-JAVA）通过 RocketMQ + HTTP + MinIO 通信，功能对等 Python 执行器（sjdt_worker），可独立部署、并行共存。

## 技术栈
- Java 21 + Spring Boot 3.3.6（非 Web 应用，仅核心容器 + 定时任务）
- RocketMQ 客户端 4.9.7（程序化消费者，支持运行时动态消费组）
- MinIO 8.6.0（脚本下载 / 日志上传）

## 架构
```plaintext
worker-JAVA/
├── pom.xml                    # Maven 工程（spring-boot-starter-parent 3.3.6）
├── Dockerfile                 # JRE21 + python3 + setsid 运行时镜像
├── docker-compose.yml         # 本地测试编排（host 网络 + env_file .env）
├── .env.example               # 环境变量模板（复制为 .env）
└── src/main/java/com/sjdt/worker/
    ├── WorkerApplication.java       # 启动入口（@EnableScheduling）
    ├── config/                      # 配置装配（WorkerProperties / MinioConfig）
    ├── common/                      # 本地配置存储 / Worker上下文 / 标签 / 系统指标
    ├── client/                      # AKSK 签名 HTTP 客户端 / MinIO 存储
    ├── model/                       # 任务/结果/控制消息 DTO
    ├── service/                     # 生命周期 / 心跳 / 状态上报 / MQ / 任务分发 / 进程管理
    └── handler/                     # python / java / go 处理器（模板方法）
```

## 核心流程
1. **启动注册**：加载 `local_config.json` → 验证 AK/SK（`/api/worker/validate-aksk`）→ 无效则注册（`/api/worker/register`）→ 初始化上下文 → 启动 MQ。
2. **任务消费**：独立消费组 `WORKER_EXECUTOR_GROUP_{workerId}` 订阅 `TASK_EXECUTE`（Tag=workerId，Broker 定向投递）。
3. **任务执行**：下载脚本（MinIO）→ 启动子进程（setsid 独立会话）→ 日志流式上传（`task-logs/{taskId}/{runId}.log`）→ 结果发 `TASK_RESULT` + 状态经 `/api/worker/task-status` 持续上报。
4. **控制指令**：广播消费组 `WORKER_EXECUTOR_GROUP_CONTROL` 订阅 `TASK_CONTROL`，处理 STOP/PAUSE/RESUME/DELETE/KILL/UPDATE_TAGS。
5. **周期上报**：心跳（2s）/ 系统状态（10s）/ 运行任务状态（5s）/ 标签变化（60s）。

## 构建与部署
```bash
cd worker-JAVA
mvn clean package -DskipTests        # 产出 target/sjdt-worker.jar
docker build -t sjdt-worker-java:1.0 .
cd docker 2>/dev/null || cd .
cp ../.env.example .env              # 按需修改
docker compose up -d
```

## 通信契约要点
- 签名：`X-Signature = HMAC-SHA256(SK, timestamp)`（timestamp 秒级），头 `X-Access-Key / X-Timestamp / X-Signature`；注册与 AKSK 验证除外。
- 时间字段统一输出无 Z 的 ISO_LOCAL_DATE_TIME（如 `2025-05-20T15:30:00`），与调度器 `LocalDateTime` 反序列化互操作；运行中上报 `endTime` 置 null 省略。
- 任务结果经 MQ `TASK_RESULT` 回报，调度器消费组 `SCHEDULER_RESULT_GROUP`。
