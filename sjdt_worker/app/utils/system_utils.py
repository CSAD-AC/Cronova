import psutil
from threading import Lock


class SystemUtils:
    # 运行中任务数的计数器
    _running_tasks = 0
    _lock = Lock()

    @staticmethod
    def get_cpu_usage() -> float:
        """获取CPU使用率（百分比）"""
        return psutil.cpu_percent(interval=0.1)

    @staticmethod
    def get_mem_usage() -> float:
        """获取内存使用率（百分比）"""
        mem = psutil.virtual_memory()
        return mem.percent

    @staticmethod
    def increment_running_tasks() -> None:
        """增加运行中任务数（线程安全）"""
        with SystemUtils._lock:
            SystemUtils._running_tasks += 1
            print(f"运行中任务数+1，当前：{SystemUtils._running_tasks}")

    @staticmethod
    def decrement_running_tasks() -> None:
        """减少运行中任务数（线程安全）"""
        with SystemUtils._lock:
            if SystemUtils._running_tasks > 0:
                SystemUtils._running_tasks -= 1
            print(f"运行中任务数-1，当前：{SystemUtils._running_tasks}")

    @staticmethod
    def get_running_tasks() -> int:
        """获取当前运行中的任务数"""
        with SystemUtils._lock:
            return SystemUtils._running_tasks
