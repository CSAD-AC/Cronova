"""
数据同步回调示例（适配新版 ScheduledJobBase）

- 不再继承自动路由注册逻辑
- 子类仅实现业务，路由由外部显式注册
"""

import time
import random
from flask import Flask, request

from scheduled_job_base import ScheduledJobBase, Result


class DataSyncService:
    """模拟的数据同步服务"""

    def sync(self, sync_type: str, date: str) -> dict:
        """
        执行数据同步逻辑
        """
        start_time = time.time()
        print(f"[模拟] 执行 {sync_type} 同步，日期：{date}")

        # 模拟耗时操作
        time.sleep(1 + random.random() * 2)

        records = 100 + random.randint(0, 900)
        duration_ms = int((time.time() - start_time) * 1000)

        return {
            "records_processed": records,
            "duration_ms": duration_ms,
            "status": "success",
            "message": f"成功同步 {records} 条记录"
        }


class DataSyncCallback(ScheduledJobBase):
    """数据同步任务处理器"""

    def __init__(self):
        # 不再接收 app，不自动注册路由
        self._service = DataSyncService()

    def secret_key(self) -> str:
        """返回用于 X-Secret 校验的密钥"""
        return "data-sync-secret-key-2026"

    def run_job(self, param: dict) -> Result:
        """
        执行具体同步任务
        param 中已包含 request_id（由基类注入）
        """
        request_id = param.get("request_id")
        sync_type = param.get("syncType", "delta")
        date = param.get("date", "2026-01-01")

        print(f"[{request_id}] 开始执行数据同步任务: syncType={sync_type}, date={date}")

        result_data = self._service.sync(sync_type, date)

        print(f"[{request_id}] 同步完成: {result_data['records_processed']} 条记录")

        return Result.ok(result_data)


# ===== Flask 应用入口 =====
app = Flask(__name__)


@app.route("/api/callback/data-sync/job", methods=["POST"])
def handle_data_sync():
    """手动注册路由，调用基类统一处理入口"""
    job = DataSyncCallback()
    return job._handle()


if __name__ == "__main__":
    print("✅ 数据同步回调服务已启动")
    print("📌 路由: POST /api/callback/data-sync/job")
    print("🔑 密钥: data-sync-secret-key-2026")
    print("📝 请求头示例:")
    print("   X-Request-Id: req-12345")
    print("   X-Secret: data-sync-secret-key-2026")
    print("📦 Body (可选): {\"syncType\": \"full\", \"date\": \"2026-01-15\"}")
    print("-" * 50)
    app.run(host="0.0.0.0", port=8080, debug=True)