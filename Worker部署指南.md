# Worker Docker 部署指南

## 快速操作流程

### 增删依赖

**添加新依赖**：
```bash
# 1. 编辑 requirements.txt
echo "requests>=2.28.0" >> sjdt_worker/requirements.txt

# 2. 重新构建镜像
cd sjdt_worker
docker build -f docker/Dockerfile -t sjdt-worker:1.0 .

# 3. 重启容器
cd docker
docker-compose restart
```

**删除依赖**：
```bash
# 1. 编辑 requirements.txt，删除对应行
# 2. 重新构建镜像（同上）
# 3. 重启容器（同上）
```

**⚠️ 注意**：requirements.txt 中的 SQLAlchemy 使用了修改后的 GBase 连接兼容版本：
```
# SQLAlchemy: 使用自定义 GBase 兼容版本
git+https://github.com/CSAD-AC/sqlalchemy.git@v2.0.36-gbase#egg=SQLAlchemy
```

---

### 增删工具类

**添加新工具类**：
```bash
# 1. 在 sjdt_worker/app/scripts_tmp/sjdt_utils/ 下创建新文件（如 helper.py）
cat > sjdt_worker/app/scripts_tmp/sjdt_utils/helper.py << 'EOF'
def hello():
    return "Hello from helper"
EOF

# 2. 在 sjdt_worker/app/scripts_tmp/sjdt_utils/__init__.py 中添加导出
# 添加: from .helper import hello

# 3. 重新构建镜像
cd sjdt_worker
docker build -f docker/Dockerfile -t sjdt-worker:1.0 .

# 4. 重启容器
cd docker
docker-compose restart
```

**删除工具类**：
```bash
# 1. 删除 sjdt_worker/app/scripts_tmp/sjdt_utils/xxx.py 文件
# 2. 从 sjdt_worker/app/scripts_tmp/sjdt_utils/__init__.py 中移除导入
# 3. 重新构建镜像并重启容器
```

**使用方式**（在任意 Python 脚本中）：
```python
from sjdt_utils import hello
result = hello()
```

---

### 增删标签

**修改标签**：
```bash
# 1. 编辑 .env 文件
vim sjdt_worker/docker/.env

# 2. 修改 WORKER_TAGS 行，例如：
# 添加标签: WORKER_TAGS=V1,Python3.11,GPU
# 删除标签: WORKER_TAGS=V1

# 3. 无需重新构建，直接重启容器
cd sjdt_worker/docker
docker-compose restart
```

---

## 常用命令

```bash
# 进入 Docker 目录
cd sjdt_worker/docker

# 构建镜像
cd ../../
docker build -f sjdt_worker/docker/Dockerfile -t sjdt-worker:1.0 sjdt_worker/

# 启动容器
cd sjdt_worker/docker
docker-compose up -d

# 查看日志
docker logs -f sjdt-worker

# 重启容器
docker-compose restart

# 停止容器
docker-compose down

# 删除容器和卷
docker-compose down -v
```

---
  
## 环境变量说明

| 变量名 | 说明 | 示例 |
|--------|------|------|
| SCHEDULER_BASE_URL | 调度器地址 | http://host.docker.internal:8085 |
| WORKER_TAGS | Worker 标签（逗号分隔） | V1,Python3.11,GPU |
| HEARTBEAT_INTERVAL | 心跳间隔（秒） | 2 |
| STATUS_REPORT_INTERVAL | 状态上报间隔（秒） | 10 |
| MQ_NAMESRV_ADDR | RocketMQ 地址 | 127.0.0.1:9876 |
| MQ_CONSUMER_GROUP | 消费者组 | WORKER_EXECUTOR_GROUP |
| MQ_TASK_TOPIC | 任务主题 | TASK_EXECUTE |
| MQ_TASK_RESULT_TOPIC | 结果主题 | TASK_RESULT |
| MINIO_ENDPOINT | MinIO 服务地址 | 127.0.0.1:9000 |
| MINIO_ACCESS_KEY | MinIO 访问密钥 | changeme |
| MINIO_SECRET_KEY | MinIO 密钥 | changeme |
| MINIO_BUCKET_NAME | 存储桶名称 | task-engine |

---

## 文件结构

```
sjdt_worker/
├── app/
│   ├── scripts_tmp/           # 工具类目录
│   │   ├── sjdt_utils/       # 工具类包
│   │   │   ├── __init__.py  # 导出定义
│   │   │   └── logger.py    # 示例工具类
│   │   └── pyproject.toml   # 工具类包配置
│   └── ...
├── docker/
│   ├── .env                  # 环境变量（修改后重启生效）
│   ├── Dockerfile            # 镜像构建文件
│   └── docker-compose.yml    # 容器编排
├── requirements.txt          # Python 依赖
└── main.py                  # 入口文件
```

---

## 注意事项

1. **修改 .env 文件**：无需重新构建，直接重启容器即可生效
2. **修改 requirements.txt 或工具类**：需要重新构建镜像
3. **构建上下文**：必须在 `sjdt_task-engine` 根目录执行构建命令
