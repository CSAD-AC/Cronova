from app.config import Config
from app.http_client import HttpClient
from app.model.worker import HeartbeatResponse, StatusReportResponse
from app.utils.system_utils import SystemUtils


class ReportService:
    @staticmethod
    def send_heartbeat() -> bool:
        """发送心跳（2秒一次）"""
        if not Config.WORKER_ID:
            print("未注册Worker，无法发送心跳")
            return False
        # 显式传递空JSON，触发Content-Type: application/json头
        response = HttpClient.post(Config.WORKER_HEARTBEAT_URL, json_data={})
        if not response:
            return False
        # 解析响应
        try:
            heartbeat_resp = HeartbeatResponse(**response)
            return heartbeat_resp.code == 200
        except Exception as e:
            print(f"解析心跳响应失败：{str(e)}")
            return False

    @staticmethod
    def report_status() -> bool:
        """上报状态（10秒一次）"""
        if not Config.WORKER_ID:
            print("未注册Worker，无法上报状态")
            return False
        # 获取系统信息并构造状态上报数据
        cpu_usage = SystemUtils.get_cpu_usage()
        mem_usage = SystemUtils.get_mem_usage()
        running_tasks = SystemUtils.get_running_tasks()
        status_data = {
            "cpuUsage": cpu_usage,
            "memUsage": mem_usage,
            "runningTasks": running_tasks
        }
        response = HttpClient.post(Config.WORKER_STATUS_REPORT_URL, params=status_data, json_data={})
        if not response:
            return False
        try:
            status_resp = StatusReportResponse(**response)
            return True
        except Exception as e:
            print(f"解析状态上报响应失败：{str(e)}")
            return False