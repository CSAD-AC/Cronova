# 执行器 (Worker) 对接文档
## 1.概述
执行器 (Worker) 是任务调度系统的核心组件，负责接收并执行调度器下发的任务（如 Python 脚本、Java 程序等），并将执行结果反馈给调度器。本文档详细说明执行器的对接方式、通信协议及核心功能实现。
## 2.项目架构
```plaintext
项目根目录/
├── app/                  # 核心应用代码
│   ├── config.py         # 全局配置（接口地址、MQ/MinIO配置、动态参数等）
│   ├── http_client.py    # HTTP客户端工具（封装请求及认证逻辑）
│   ├── mq_consumer.py    # RocketMQ消费者（监听任务队列、分发任务）
│   ├── local_config.json # 本地配置文件（存储WorkerID、AKSK等持久化信息）
│   ├── model/            # 数据模型定义（请求/响应结构、任务模型等）
│   │   ├── task.py       # 任务执行/结果消息模型
│   │   └── worker.py     # Worker注册/认证/心跳等相关模型
│   ├── service/          # 核心业务服务
│   │   ├── auth_service.py    # AKSK验证服务
│   │   ├── register_service.py # Worker注册服务
│   │   ├── report_service.py  # 心跳/状态上报服务
│   │   ├── task_service.py    # 任务分发服务（调用对应处理器）
│   │   └── task_handler/      # 任务处理器（按脚本类型分类）
│   │       ├── base_handler.py  # 处理器基类
│   │       ├── python_handler.py # Python脚本处理器
│   │       └── java_handler.py   # Java脚本处理器
│   └── utils/            # 工具类
│       ├── auth_utils.py    # 签名生成工具
│       ├── env_utils.py     # 环境信息（如安装包版本）工具
│       ├── minio_utils.py   # MinIO文件操作
│       ├── ip_utils.py      # IP/主机名获取工具
│       └── system_utils.py  # 系统资源（CPU/内存）、任务计数工具
├── main.py               # 程序入口（启动Worker、初始化组件等）
└── README.md             # 项目说明文档
```
## 3.核心功能与流程
### 3.1 执行器生命周期
1、初始化：加载本地配置（local_config.json），包含 WorkerID、AK/SK 等信息\
2、注册：若未注册或者AK/SK验证不通过，向调度器发起注册请求，获取 WorkerID 和认证信息\
3、运行：启动消息消费者监听任务队列，处理任务并上报结果\
4、心跳与状态上报：定期发送心跳（2 秒 / 次）和系统状态（10 秒 / 次）\
5、退出：关闭消费者和资源连接\
### 3.2 核心流程详解
#### 3.2.1 注册流程
目的：执行器首次启动时向调度器注册身份，获取唯一标识（WorkerID）和认证信息（AK/SK）\
请求地址：{SCHEDULER_BASE_URL}/api/worker/register\
请求体：
```json
{
  "host": "worker-hostname",
  "ip": "192.168.1.100",
  "tags": [
    {"id": 0, "name": "numpy", "operator": "==", "version": "2.0.2", "createTime": "2025-01-01T00:00:00Z"},
    {"id": 1, "name": "python", "operator": "==", "version": "3.9.7", "createTime": "2025-01-01T00:00:00Z"}
  ]
}
```
tags：动态生成，包含执行器环境依赖（如 Python 版本、关键库版本）
响应体：返回 WorkerID、AK、SK，执行器本地持久化存储（local_config.json）
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "workerId": "6df1e5170fc21d0186b8fa4c6a2c67fb",
        "accessKey": "REDACTED",
        "secretKey": "REDACTED"
    }
}
```
#### 3.2.2 任务处理流程
1、任务接收：\
    通过 RocketMQ 订阅TASK_EXECUTE主题，接收任务消息\
2、任务过滤：\
    公共任务：所有执行器均可处理\
    定向任务：仅目标 WorkerID 匹配的执行器处理\
3、任务执行：\
    从 MinIO 下载脚本（支持 Python .py、Java .class/.jar）\
    临时文件存储与权限处理\
    执行脚本（带超时控制），捕获标准输出 / 错误\
4、结果处理：\
    日志上传至 MinIO（路径：task-logs/{taskId}.log）\
    构建结果消息（状态、耗时、日志路径等）\
    发送结果至TASK_RESULT主题\
#### 3.2.3 通信协议
| 组件        | 通信方式       | 主题 / 接口                          | 说明                     |
|-------------|----------------|-----------------------------------|--------------------------|
| 调度器→执行器 | RocketMQ       | 主题：`TASK_EXECUTE`              | 下发任务                 |
| 执行器→调度器 | RocketMQ       | 主题：`TASK_RESULT`               | 反馈任务结果             |
| 执行器→调度器 | HTTP POST      | `/api/worker/register`            | 注册请求                 |
| 执行器→调度器 | HTTP POST      | `/api/worker/heartbeat/{workerId}`| 心跳上报（2 秒/次）      |
| 执行器→调度器 | HTTP POST      | `/api/worker/status/{workerId}`   | 状态上报（10 秒/次）     |
## 4. 数据模型
### 4.1 任务消息模型（TaskExecuteMessage）
```python
class TaskExecuteMessage(BaseModel):
    taskId: str               # 任务唯一标识
    taskName: str             # 任务名称
    description: Optional[str] # 任务描述
    scriptType: str           # 脚本类型（python/java/bash）
    script: ScriptInfo        # 脚本信息
    taskType: str             # 任务类型（once/cron）
    timeoutSeconds: int       # 超时时间（秒）
    targetWorkerId: Optional[str] # 定向执行器ID（可选）
```
### 4.2 脚本信息模型（ScriptInfo）
```python
class ScriptInfo(BaseModel):
    id: str                  # 脚本ID
    name: str                # 脚本名称
    path: str                # 脚本存储路径（MinIO路径，如minio://bucket/script.py）
    tags: List[ScriptTag]    # 脚本依赖标签
```
### 4.3 任务结果模型（TaskResultMessage）
```python
class TaskResultMessage(BaseModel):
    workerId: str            # 执行器ID
    taskId: str              # 任务ID
    status: str              # 状态（pending/running/success/failed/timeout）
    exitCode: int            # 退出码（0为成功）
    startTime: str           # 开始时间（UTC，如2025-05-20T15:30:00Z）
    endTime: str             # 结束时间
    durationMs: int          # 耗时（毫秒）
    errorMessage: Optional[str] # 错误信息
    logPath: Optional[str]   # MinIO日志路径（如task-logs/task_123.log）
```
## 5. 安全认证
AK/SK 机制：执行器与调度器通信采用 AK（Access Key）和 SK（Secret Key）认证\
签名生成规则：\
拼接待签名字符串：{METHOD}\n{URL_PATH}\n{QUERY_PARAMS}\n{BODY}\n{TIMESTAMP}\
使用 HMAC-SHA256 算法，以 SK 为密钥对字符串签名\
请求头携带：X-Access-Key、X-Timestamp、X-Signature\
