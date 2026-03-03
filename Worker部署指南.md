# Worker Docker 部署指南

## 目录结构说明

```
sjdt-task-engine/                      # 项目根目录（Docker 构建上下文）
├── docs/                              # 文档目录
│   └── Worker部署指南.md               # 本文档
├── sjdt_worker/                       # Worker 项目目录
│   ├── app/
│   │   ├── scripts_tmp/              # 脚本临时目录
│   │   │   ├── sjdt_utils/          # 工具类包（Docker 构建时安装为全局依赖）
│   │   │   │   ├── __init__.py      # 包导出定义
│   │   │   │   └── logger.py        # 工具类文件示例
│   │   │   └── pyproject.toml       # 工具类包配置
│   │   └── ...
│   ├── docker/
│   │   ├── .env                      # 环境变量配置
│   │   ├── docker-compose.yml        # Docker Compose 配置
│   │   └── Dockerfile                # Docker 构建文件
│   ├── requirements.txt              # Python 依赖管理
│   └── main.py                       # Worker 入口文件
└── ...
```

## 关键目录说明

### 1. 工具类目录
- **路径**: `sjdt_worker\app\scripts_tmp\sjdt_utils`
- **用途**: 放置工具类文件，在 Docker 构建时通过 pip 安装为全局依赖
- **结构说明**:
  - `sjdt_utils/__init__.py` - 包导出定义，需在此文件中添加新模块的导入
  - `sjdt_utils/logger.py` - 工具类文件示例
  - `pyproject.toml` - 工具类包的配置文件
- **添加新工具类的步骤**:
  1. 在 `sjdt_utils/` 目录下创建新的工具类文件（如 `helper.py`）
  2. 在 `sjdt_utils/__init__.py` 中添加导出语句
  3. 在 `pyproject.toml` 中无需修改（已配置自动发现）
  4. 重新构建 Docker 镜像
- **使用方式**: 在任意 Python 脚本中直接导入 `from sjdt_utils import xxx`

### 2. 依赖管理
- **路径**: `sjdt_worker\requirements.txt`
- **用途**: 管理 Worker 所需的 Python 依赖
- **添加依赖**: 修改此文件后重新构建镜像

### 3. 环境变量配置
- **路径**: `sjdt_worker\docker\.env`
- **用途**: 配置 Worker 运行时所需的环境变量

## Docker 构建与部署

### 1. 构建镜像

在项目根目录执行（注意是 `sjdt-task-engine` 目录，不是 `sjdt_worker` 目录）：

```bash
cd sjdt-task-engine\sjdt_worker
docker build -f docker/Dockerfile -t sjdt-worker:1.0 .
```

### 2. 运行容器

使用 docker-compose 运行（推荐）：

```bash
cd sjdt_worker\docker
docker-compose up -d
```

### 3. 常用命令

```bash
# 查看运行日志
docker logs -f sjdt-worker

# 重启容器
docker-compose restart

# 停止容器
docker-compose down

# 删除容器
docker-compose down -v
```

## 注意事项

### 1. 构建上下文
- Docker 构建必须在项目根目录（`sjdt-task-engine`）执行
- 不能在 `sjdt_worker` 目录执行，否则无法正确复制文件

### 2. 工具类管理
- 工具类包位于 `sjdt_worker/app/scripts_tmp/sjdt`
- 添加新工具类需要修改_utils/以下文件：
  - `sjdt_utils/__init__.py` - 添加新的导入和导出
  - `sjdt_utils/` 目录下创建新的模块文件
- 修改后需要重新构建 Docker 镜像才能生效

### 3. 依赖更新
- 修改 `requirements.txt` 后必须重新构建镜像
- 构建时会自动安装所有依赖

### 4. 脚本临时目录
- 容器内脚本临时目录为 `/app/app/scripts_tmp/`
- 执行脚本时会自动创建子目录存放脚本文件
- 执行完成后自动清理

### 5. 环境变量
- 修改 `.env` 文件后无需重新构建镜像
- 直接重启容器即可生效

## 环境变量参考

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| SCHEDULER_BASE_URL | 调度器地址 | http://host.docker.internal:8085 |
| WORKER_TAGS | Worker 标签（逗号分隔，用于任务匹配） | V1,Python3.11 |
| HEARTBEAT_INTERVAL | 心跳间隔（秒） | 2 |
| STATUS_REPORT_INTERVAL | 状态上报间隔（秒） | 10 |
| MQ_NAMESRV_ADDR | RocketMQ 地址 | 127.0.0.1:9876 |
| MQ_CONSUMER_GROUP | 消费者组 | WORKER_EXECUTOR_GROUP |
| MQ_PRODUCER_GROUP | 生产者组 | WORKER_EXECUTOR_GROUP |
| MQ_TASK_TOPIC | 任务主题 | TASK_EXECUTE |
| MQ_TASK_RESULT_TOPIC | 结果主题 | TASK_RESULT |
| MQ_TASK_CONTROL_TOPIC | 控制主题 | TASK_CONTROL |
| MINIO_ENDPOINT | MinIO 服务地址 | 127.0.0.1:9000 |
| MINIO_ACCESS_KEY | MinIO 访问密钥 | changeme |
| MINIO_SECRET_KEY | MinIO 密钥 | changeme |
| MINIO_SECURE | 是否 HTTPS | False |
| MINIO_BUCKET_NAME | 存储桶名称 | task-engine |
