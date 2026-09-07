import os
import signal
import threading
import subprocess
import time
from datetime import datetime
from typing import Dict, Optional, Any
from app.model.task import TaskExecuteMessage
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class ProcessManager:
    """进程管理器，负责管理所有运行中的任务进程"""

    def __init__(self):
        self._processes: Dict[str, Dict[str, Any]] = {}
        self._paused_processes: Dict[str, bool] = {}
        self._lock = threading.Lock()

    def add_process(self, task_run_id: str, process_info: Dict[str, Any]) -> None:
        """添加一个运行中的进程"""
        with self._lock:
            self._processes[task_run_id] = process_info
            self._paused_processes[task_run_id] = False
            logger.info("添加运行中进程，taskRunId: %s", task_run_id)

    def remove_process(self, task_run_id: str) -> Optional[Dict[str, Any]]:
        """移除一个完成的进程"""
        with self._lock:
            process_info = self._processes.pop(task_run_id, None)
            self._paused_processes.pop(task_run_id, None)
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

    def is_paused(self, task_run_id: str) -> bool:
        """检查指定任务是否已暂停"""
        with self._lock:
            return self._paused_processes.get(task_run_id, False)

    def stop_process(self, task_run_id: str, force: bool = False) -> bool:
        """停止指定任务进程"""
        with self._lock:
            process_info = self._processes.get(task_run_id)
            if process_info:
                process = process_info.get('process')
                if process and process.poll() is None:
                    try:
                        if force:
                            process.kill()
                            process.wait(timeout=5)
                            logger.info("强制终止任务进程(SIGKILL)，taskRunId: %s", task_run_id)
                        else:
                            process.terminate()
                            process.wait(timeout=5)
                            logger.info("正常终止任务进程(SIGTERM)，taskRunId: %s", task_run_id)
                        
                        self._processes.pop(task_run_id, None)
                        self._paused_processes.pop(task_run_id, None)
                        logger.info("从进程管理器中移除已终止进程，taskRunId: %s", task_run_id)
                        return True
                    except subprocess.TimeoutExpired:
                        process.kill()
                        logger.warning("强制终止任务进程(SIGKILL，因超时)，taskRunId: %s", task_run_id)
                        self._processes.pop(task_run_id, None)
                        self._paused_processes.pop(task_run_id, None)
                        return True
                    except Exception as e:
                        logger.error("停止任务进程失败，taskRunId: %s，错误: %s", task_run_id, str(e))
                        return False
            return False

    def pause_process(self, task_run_id: str) -> bool:
        """暂停指定任务进程（发送SIGSTOP信号到进程组）"""
        with self._lock:
            process_info = self._processes.get(task_run_id)
            if process_info:
                process = process_info.get('process')
                if process and process.poll() is None:
                    try:
                        process_pid = process.pid
                        process_group_id = os.getpgid(process_pid)
                        
                        logger.info("开始暂停任务进程，taskRunId: %s, PID: %d, PGID: %d", 
                                   task_run_id, process_pid, process_group_id)
                        
                        try:
                            os.killpg(process_group_id, signal.SIGSTOP)
                            logger.info("暂停进程组成功(SIGSTOP到PGID)，taskRunId: %s, PGID: %d", 
                                       task_run_id, process_group_id)
                        except ProcessLookupError:
                            logger.warning("进程组不存在，尝试暂停主进程，taskRunId: %s", task_run_id)
                            process.send_signal(signal.SIGSTOP)
                        
                        self._paused_processes[task_run_id] = True
                        
                        time.sleep(0.1)
                        
                        if process.poll() is not None:
                            logger.error("暂停后进程已终止，taskRunId: %s", task_run_id)
                            return False
                            
                        logger.info("暂停任务进程成功，taskRunId: %s", task_run_id)
                        return True
                    except Exception as e:
                        logger.error("暂停任务进程失败，taskRunId: %s，错误: %s", task_run_id, str(e))
                        return False
            logger.warning("任务进程不存在或已停止，taskRunId: %s", task_run_id)
            return False

    def resume_process(self, task_run_id: str) -> bool:
        """恢复指定任务进程（发送SIGCONT信号到进程组）"""
        with self._lock:
            process_info = self._processes.get(task_run_id)
            if process_info:
                process = process_info.get('process')
                if process and process.poll() is None:
                    try:
                        process_pid = process.pid
                        process_group_id = os.getpgid(process_pid)
                        
                        logger.info("开始恢复任务进程，taskRunId: %s, PID: %d, PGID: %d", 
                                   task_run_id, process_pid, process_group_id)
                        
                        try:
                            os.killpg(process_group_id, signal.SIGCONT)
                            logger.info("恢复进程组成功(SIGCONT到PGID)，taskRunId: %s, PGID: %d", 
                                       task_run_id, process_group_id)
                        except ProcessLookupError:
                            logger.warning("进程组不存在，尝试恢复主进程，taskRunId: %s", task_run_id)
                            process.send_signal(signal.SIGCONT)
                        
                        self._paused_processes[task_run_id] = False
                        
                        logger.info("恢复任务进程成功，taskRunId: %s", task_run_id)
                        return True
                    except Exception as e:
                        logger.error("恢复任务进程失败，taskRunId: %s，错误: %s", task_run_id, str(e))
                        return False
            logger.warning("任务进程不存在或已停止，taskRunId: %s", task_run_id)
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
                self._paused_processes.pop(task_run_id, None)
                logger.debug("清理已完成进程，taskRunId: %s", task_run_id)


# 全局进程管理器实例
process_manager = ProcessManager()
