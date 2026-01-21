# Python 回调任务对接文档

## 一、概述

本文档指导 Python 项目如何对接任务调度器的回调功能。当调度器定时检测到所设置的定时任务后，调度器会向配置的回调 URL 发送 HTTP 请求，接收方需要：

1. **校验请求头**：验证 `X-Secret` 密钥和 `X-Request-Id` 请求 ID
2. **执行业务逻辑**：执行定时任务的实际逻辑
3. **返回结果**：使用统一的 `Result` 对象返回执行结果

## 二、请求规范

### 2.1 请求方法

回调接口使用不同的 HTTP 方法：
- **任务回调接口**：POST 方法
- **健康检查接口**：GET 方法

### 2.2 请求头说明

| 请求头 | 必填 | 说明 |
|--------|------|------|
| `X-Request-Id` | 是 | 请求唯一标识，用于日志追踪和请求关联 |
| `X-Secret` | 是 | 密钥，用于身份验证，需与回调配置中的密钥一致 |

**请求头示例**：
```http
POST /api/callback/data-sync HTTP/1.1
Host: your-service.com
X-Request-Id: abc123def456
X-Secret: your-secret-key
```

### 2.3 响应规范

所有回调接口必须返回统一的 JSON 格式，包含 `code`、`message`、`data` 字段。详细响应示例请参见各接口说明：
- 任务回调接口响应：见 [3.4 响应示例](#34-响应示例)
- 健康检查接口响应：见 [4.3 响应示例](#43-响应示例)

## 三、任务回调接口

### 3.1 接口说明

任务回调接口是调度器触发任务执行时调用的主接口，用于接收调度器的执行请求并执行业务逻辑。

**接口约定**：
- **路径格式**：`<回调接口路径>`
- **示例**：`/api/callback/data-sync`
- **请求方法**：POST
- **功能**：接收调度器任务执行请求，执行业务逻辑，返回执行结果

### 3.2 请求示例

```http
POST /api/callback/data-sync HTTP/1.1
Host: your-service.com
X-Request-Id: task-exec-123456
X-Secret: your-secret-key
Content-Type: application/json

```


### 3.3 响应示例

**成功**：
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        [data内容可自行设计]
        "success": true,
        "recordsProcessed": 100,
        "durationMs": 1500
    }
}
```

**失败**：
```json
{
    "code": 500,
    "message": "执行失败: 数据库连接超时"
}
```

## 四、健康检查接口

### 4.1 接口说明

健康检查接口用于验证回调服务是否正常运行，验证请求头（X-Secret 和 X-Request-Id）。

**接口约定**：
- **路径格式**：`<回调接口路径>/health`（强制约定）
- **示例**：
  - 回调接口：`/api/callback/data-sync`
  - 健康接口：`/api/callback/data-sync/health`
- **请求方法**：GET
- **功能**：仅验证 X-Secret 和 X-Request-Id 请求头，不执行业务逻辑
- **要求**：健康接口和回调接口必须使用相同的 BaseUrl

### 4.2 请求示例

```http
GET /api/callback/data-sync/health HTTP/1.1
Host: your-service.com
X-Request-Id: abc123def456
X-Secret: your-secret-key
```

### 4.3 响应示例

**成功**：
```json
{
    "code": 200,
    "message": "操作成功",
    "data": {
        "endpoint": "同步H4A员工信息服务",
        "message": "服务正常",
        "status": "UP",    * 核心, 固定为 "UP" *
        "timestamp": "2026-01-20T10:03:24.462013500"
    }
}
```

**失败（密钥错误）**：
```json
{
    "code": 401,
    "message": "密钥验证失败"
}
```

**失败（缺少请求头）**：
```json
{
    "code": 400,
    "message": "缺少请求头 X-Request-Id"
}
```

### 4.4 data 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `status` | string | 状态，固定为 "UP" |
| `timestamp` | string | 时间戳，格式：yyyy-MM-dd'T'HH:mm:ss |
| `endpoint` | string | 模块名称，如"数据同步服务"、"报表生成服务" |
| `message` | string | 状态描述，固定为 "服务正常" |

## 五、快速集成

### 5.1 引入基类

将 `scheduled_job_base.py` 复制到你的项目中，该文件提供了：

- `Result` 类：统一的返回对象
- `ScheduledJobBase` 类：回调处理基类

### 5.2 创建回调处理器

创建处理器类继承 `ScheduledJobBase`，并实现三个方法：

```python
from flask import Flask, request
from scheduled_job_base import ScheduledJobBase, Result

app = Flask(__name__)


class MyJobCallback(ScheduledJobBase):
    """自定义任务回调处理器"""

    def secret_key(self) -> str:
        """
        返回密钥，用于 X-Secret 校验
        【注意】此密钥需要与调度器回调配置中的密钥一致
        """
        return "your-secret-key-here"

    def endpoint(self) -> str:
        return "我的任务服务"

    def run_job(self, param: dict) -> Result:
        """
        执行业务逻辑
        【注意】日志中必须带上 request_id，便于问题排查
        """
        request_id = param.get("request_id", "")
        
        # 【规范】日志开头带上 [request_id]
        print(f"[{request_id}] 开始执行任务，参数: {param}")

        try:
            result_data = self._do_work(param)
            print(f"[{request_id}] 任务执行成功，结果: {result_data}")
            return Result.ok(result_data)

        except Exception as e:
            print(f"[{request_id}] 任务执行失败: {str(e)}")
            return Result.fail(500, f"执行失败: {str(e)}")

    def _do_work(self, param: dict) -> dict:
        """业务逻辑实现"""
        return {"status": "completed", "processed": 100}
```

### 5.3 注册路由

```python
@app.route("/api/callback/my-job", methods=["POST"])
def handle_my_job():
    """回调入口"""
    job = MyJobCallback()
    return job._handle()


@app.route("/api/callback/my-job/health", methods=["GET"])
def my_job_health():
    """健康检查接口"""
    job = MyJobCallback()
    return job._health_check()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8080, debug=True)
```

## 六、完整示例

### 6.1 完整的回调服务示例

```python
"""
数据同步回调服务

【接口说明】
1. 回调接口：POST /api/callback/data-sync
2. 健康接口：GET  /api/callback/data-sync/health
"""

import time
import random
from flask import Flask, request
from typing import Any, Dict
from scheduled_job_base import ScheduledJobBase, Result


class DataSyncService:
    """数据同步服务"""

    def sync(self, sync_type: str, date: str) -> Dict[str, Any]:
        """
        执行数据同步

        Args:
            sync_type: 同步类型 (full/delta)
            date: 同步日期

        Returns:
            同步结果
        """
        print(f"[模拟] 执行 {sync_type} 同步，日期：{date}")

        # 模拟耗时操作
        time.sleep(1 + random.random() * 2)

        records = 100 + random.randint(0, 900)
        duration_ms = int(time.time() * 1000) % 10000

        return {
            "records_processed": records,
            "duration_ms": duration_ms,
            "status": "success",
            "message": f"成功同步 {records} 条记录"
        }


class DataSyncCallback(ScheduledJobBase):
    """数据同步回调处理器"""

    def __init__(self):
        self._service = DataSyncService()

    def secret_key(self) -> str:
        """
        返回密钥
        【注意】此密钥需要与调度器回调配置中的密钥保持一致
        """
        return "data-sync-secret-key-2026"

    def endpoint(self) -> str:
        return "数据同步服务"

    def run_job(self, param: Dict[str, Any]) -> Result:
        """
        执行业务逻辑
        param 中已包含 request_id（由基类自动注入）
        """
        request_id = param.get("request_id", "")
        sync_type = param.get("syncType", "delta")
        date = param.get("date", "2024-01-01")

        # 【规范】日志中必须带上 [request_id]
        print(f"[{request_id}] 开始执行数据同步任务")
        print(f"[{request_id}] 参数: syncType={sync_type}, date={date}")

        try:
            start_time = time.time()

            result_data = self._service.sync(sync_type, date)

            duration_ms = int((time.time() - start_time) * 1000)
            print(f"[{request_id}] 同步完成: {result_data['records_processed']} 条记录，耗时: {duration_ms}ms")

            return Result.ok(result_data)

        except Exception as e:
            print(f"[{request_id}] 同步失败: {str(e)}")
            return Result.fail(500, f"数据同步失败: {str(e)}")


# ===== Flask 应用 =====
app = Flask(__name__)


@app.route("/api/callback/data-sync", methods=["POST"])
def handle_data_sync():
    """回调接口"""
    job = DataSyncCallback()
    return job._handle()


@app.route("/api/callback/data-sync/health", methods=["GET"])
def data_sync_health():
    """健康检查接口"""
    job = DataSyncCallback()
    return job._health_check()


# ===== 启动入口 =====
if __name__ == "__main__":
    print("=" * 50)
    print("✅ 数据同步回调服务已启动")
    print("📌 回调接口: POST /api/callback/data-sync")
    print("📌 健康接口: GET  /api/callback/data-sync/health")
    print("🔑 密钥: data-sync-secret-key-2026")
    print("=" * 50)

    app.run(host="0.0.0.0", port=8080, debug=True)
```

### 6.2 Result 类说明

```python
class Result:
    """统一返回对象"""

    def __init__(self):
        self.code = 200
        self.msg = "success"
        self.data = None
        self.cost_millis = 0
        self.request_id = None

    @classmethod
    def ok(cls, data: Any = None) -> "Result":
        """创建成功响应"""
        result = cls()
        result.code = 200
        result.msg = "success"
        result.data = data
        return result

    @classmethod
    def fail(cls, code: int, msg: str) -> "Result":
        """创建失败响应"""
        result = cls()
        result.code = code
        result.msg = msg
        return result

    def to_dict(self) -> Dict:
        """转换为字典"""
        return {
            "code": self.code,
            "msg": self.msg,
            "data": self.data,
            "cost_millis": self.cost_millis,
            "request_id": self.request_id
        }
```

## 七、日志规范

### 7.1 为什么要记录 RequestId

当出现问题需要排查时，通过 `X-Request-Id` 可以快速定位一次请求的所有日志。

### 7.2 日志示例

```python
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ExampleCallback(ScheduledJobBase):
    def secret_key(self) -> str:
        return "example-secret"

    def endpoint(self) -> str:
        return "示例服务"

    def run_job(self, param: dict) -> Result:
        request_id = param.get("request_id", "")

        # 【规范】日志开头带上 [request_id]
        logger.info(f"[{request_id}] 任务开始执行")

        try:
            # 业务逻辑
            result = self._do_work(param)

            logger.info(f"[{request_id}] 任务执行成功，结果: {result}")
            return Result.ok(result)

        except Exception as e:
            logger.error(f"[{request_id}] 任务执行失败: {str(e)}")
            return Result.fail(500, f"执行失败: {str(e)}")
```

### 7.3 日志格式建议

```
[2024-01-19 10:30:00.123] INFO  [abc123def456] 任务开始执行
[2024-01-19 10:30:01.456] INFO  [abc123def456] 任务执行成功
[2024-01-19 10:30:02.000] ERROR [abc123def456] 任务执行失败: 数据格式错误
```

## 八、使用其他 Web 框架

### 8.1 FastAPI 示例

```python
from fastapi import FastAPI, Header, HTTPException
from typing import Optional, Dict, Any
import time

app = FastAPI()


class ResponseBody(BaseModel):
    code: int
    msg: str
    data: Optional[Dict[str, Any]] = None
    cost_millis: int = 0
    request_id: str = ""


SECRET_KEY = "your-secret-key"


@app.post("/api/callback/job", response_model=ResponseBody)
async def handle_callback(
    x_request_id: str = Header(..., alias="X-Request-Id"),
    x_secret: str = Header(..., alias="X-Secret")
):
    """回调入口"""
    start_time = time.time()

    # 校验密钥
    if x_secret != SECRET_KEY:
        raise HTTPException(status_code=401, detail="密钥验证失败")

    # 记录日志
    print(f"[{x_request_id}] 开始执行任务")

    try:
        # 执行业务逻辑
        result = await self._do_work()

        cost_millis = int((time.time() - start_time) * 1000)
        print(f"[{x_request_id}] 任务执行成功，耗时: {cost_millis}ms")

        return ResponseBody(
            code=200,
            msg="success",
            data=result,
            cost_millis=cost_millis,
            request_id=x_request_id
        )

    except Exception as e:
        cost_millis = int((time.time() - start_time) * 1000)
        print(f"[{x_request_id}] 任务执行失败: {str(e)}")

        raise HTTPException(
            status_code=500,
            detail={
                "code": 500,
                "msg": f"执行失败: {str(e)}",
                "cost_millis": cost_millis,
                "request_id": x_request_id
            }
        )
```

## 九、常见问题

### Q1: 密钥验证失败怎么办？

1. 检查调度器配置中的密钥与代码中 `secret_key()` 返回的密钥是否一致
2. 检查请求头是否正确传递了 `X-Secret`
3. 检查密钥是否包含特殊字符

### Q2: 如何处理请求体为 null 的情况？

基类已经处理了这种情况，会自动将 `None` 请求体转换为空字典：

```python
# scheduled_job_base.py 中的处理
param = request.get_json(silent=True) or {}
```

### Q3: 如何在业务逻辑中获取 RequestId？

`request_id` 会被自动注入到 `param` 字典中：

```python
def run_job(self, param: dict) -> Result:
    request_id = param.get("request_id", "")
    # 使用 request_id 进行日志记录
    print(f"[{request_id}] 任务开始")
```

### Q4: 返回结果中的 data 字段应该如何组织？

`data` 字段可以是任意 JSON 结构，建议包含以下信息：

```python
result_data = {
    "summary": "执行结果摘要",
    "details": {
        "processed": 100,
        "failed": 0
    },
    "metrics": {
        "duration_ms": 1500,
        "memory_used_mb": 50
    }
}
return Result.ok(result_data)
```

## 十、最佳实践

1. **日志规范**：所有日志必须带上 `[request_id]` 前缀
2. **异常处理**：捕获所有异常，记录错误日志，返回友好的错误信息
3. **性能监控**：记录执行耗时，便于性能分析
4. **幂等性**：确保回调接口支持幂等调用
5. **超时控制**：设置合理的超时时间
6. **数据验证**：对请求参数进行校验
