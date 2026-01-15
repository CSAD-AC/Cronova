import threading
import subprocess
from datetime import datetime
from typing import Dict, Optional, Any
from app.model.task import TaskExecuteMessage
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class ProcessManager:
    """进程管理器，负责管理所有运行中的任务进程"""

    def __init__(self):
        self._processes: Dict[str, Dict[str, Any]] = {}  # taskRunId -> 进程信息
        self._lock = threading.Lock()

    def add_process(self, task_run_id: str, process_info: Dict[str, Any]) -> None:
        """添加一个运行中的进程"""
        with self._lock:
            self._processes[task_run_id] = process_info
            logger.info("添加运行中进程，taskRunId: %s", task_run_id)

    def remove_process(self, task_run_id: str) -> Optional[Dict[str, Any]]:
        """移除一个完成的进程"""
        with self._lock:
            process_info = self._processes.pop(task_run_id, None)
            if process_info:
                logger.info("移除完成进程，taskRunId: %s", task_run_id)
            return process_info

    def get_process(self, task_run_id: str) -> Optional[Dict[str, Any]]:
        """获取指定taskRunId的进程信息"""
        with self._lock:
            return self._processes.get(task_run_id)

    def get_all_processes(self) -> Dict[str, Dict[str, Any]]:
        """获取所有运行中的进程"""
        with self._lock:
            return self._processes.copy()

    def is_running(self, task_run_id: str) -> bool:
        """检查指定任务是否还在运行"""
        with self._lock:
            return task_run_id in self._processes

    def stop_process(self, task_run_id: str) -> bool:
        """停止指定任务进程"""
        with self._lock:
            process_info = self._processes.get(task_run_id)
            if process_info:
                process = process_info.get('process')
                if process and process.poll() is None:
                    try:
                        process.terminate()
                        process.wait(timeout=5)
                        logger.info("成功终止任务进程，taskRunId: %s", task_run_id)
                        # 从进程管理器中移除已终止的进程
                        self._processes.pop(task_run_id, None)
                        logger.info("从进程管理器中移除已终止进程，taskRunId: %s", task_run_id)
                        return True
                    except subprocess.TimeoutExpired:
                        process.kill()
                        logger.warning("强制终止任务进程，taskRunId: %s", task_run_id)
                        # 从进程管理器中移除已终止的进程
                        self._processes.pop(task_run_id, None)
                        logger.info("从进程管理器中移除已终止进程，taskRunId: %s", task_run_id)
                        return True
                    except Exception as e:
                        logger.error("停止任务进程失败，taskRunId: %s，错误: %s", task_run_id, str(e))
                        return False
            return False

    def cleanup_finished_processes(self) -> None:
        """清理已完成但未清理的进程"""
        with self._lock:
            finished_tasks = []
            for task_run_id, process_info in self._processes.items():
                process = process_info.get('process')
                if process and process.poll() is not None:
                    finished_tasks.append(task_run_id)

            for task_run_id in finished_tasks:
                self._processes.pop(task_run_id, None)
                logger.debug("清理已完成进程，taskRunId: %s", task_run_id)


# 全局进程管理器实例
process_manager = ProcessManager()