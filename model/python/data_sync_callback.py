"""
数据同步回调服务

【接口说明】
1. 回调接口：POST /api/callback/data-sync
2. 健康接口：GET  /api/callback/data-sync/health

【健康检查接口约定】
- 路径格式：<回调接口路径>/health（强制约定）
- 当前：/api/callback/data-sync/health
- 功能：仅验证 X-Secret 和 X-Request-Id 请求头
- 要求：健康接口和回调接口必须使用相同的 BaseUrl
"""

import time
import random
from flask import Flask
from scheduled_job_base import ScheduledJobBase, Result


class DataSyncService:
    """数据同步服务"""

    def sync(self, sync_type: str, date: str) -> dict:
        """执行数据同步"""
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
        """
        返回模块名称
        用于健康检查响应的 endpoint 字段
        """
        return "数据同步服务"

    def run_job(self, param: dict) -> Result:
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

            # 执行同步逻辑
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
    """
    健康检查接口

    【重要】接口路径约定
    - 路径：<回调接口基础路径>/health
    - 当前：/api/callback/data-sync/health

    功能：仅验证请求头（X-Secret 和 X-Request-Id），不执行业务逻辑
    """
    job = DataSyncCallback()
    return job._health_check()


# ===== 启动入口 =====
if __name__ == "__main__":
    print("=" * 50)
    print("✅ 数据同步回调服务已启动")
    print("📌 回调接口: POST /api/callback/data-sync")
    print("📌 健康接口: GET  /api/callback/data-sync/health")
    print("🔑 密钥: data-sync-secret-key-2026")
    print("📝 模块名称: 数据同步服务")
    print("=" * 50)

    app.run(host="0.0.0.0", port=8080, debug=True)
