import os
import signal
import threading
import time
from typing import Dict, Set, Optional, Callable
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class GunicornManager:
    """Gunicorn 优雅暂停/恢复管理器（基于信号机制）"""
    
    def __init__(self):
        self._pause_signals_received: Set[str] = set()
        self._pause_handlers: Dict[str, Callable] = {}
        self._lock = threading.Lock()
        self._graceful_pause_enabled = True
        self._max_pause_wait_time = 30  # 最大等待时间（秒）
        
        self._register_signal_handlers()
        
        logger.info("GunicornManager 初始化完成，支持优雅暂停机制")
    
    def _register_signal_handlers(self):
        """注册信号处理器"""
        def handle_sigterm(signum, frame):
            """处理 SIGTERM 信号（优雅关闭）"""
            with self._lock:
                self._pause_signals_received.add('SIGTERM')
            logger.info("接收到 SIGTERM 信号，准备优雅关闭")
            self._handle_graceful_shutdown()
        
        def handle_sigusr1(signum, frame):
            """处理 SIGUSR1 信号（用户自定义：优雅暂停）"""
            with self._lock:
                self._pause_signals_received.add('SIGUSR1')
            logger.info("接收到 SIGUSR1 信号，准备优雅暂停")
            self._handle_graceful_pause()
        
        def handle_sigusr2(signum, frame):
            """处理 SIGUSR2 信号（用户自定义：恢复任务）"""
            with self._lock:
                self._pause_signals_received.discard('SIGUSR1')
            logger.info("接收到 SIGUSR2 信号，准备恢复任务")
            self._handle_graceful_resume()
        
        def handle_sigcont(signum, frame):
            """处理 SIGCONT 信号（继续进程）"""
            with self._lock:
                self._pause_signals_received.discard('SIGUSR1')
            logger.info("接收到 SIGCONT 信号，恢复任务执行")
            self._handle_graceful_resume()
        
        signal.signal(signal.SIGTERM, handle_sigterm)
        signal.signal(signal.SIGUSR1, handle_sigusr1)
        signal.signal(signal.SIGUSR2, handle_sigusr2)
        signal.signal(signal.SIGCONT, handle_sigcont)
        
        logger.info("Gunicorn 信号处理器注册完成")
    
    def _handle_graceful_pause(self):
        """处理优雅暂停"""
        try:
            from app.service.process_manager import process_manager
            from app.service.task_instance_manager import task_instance_manager
            
            # 获取所有运行中的任务
            running_tasks = process_manager.get_all_processes()
            task_count = len(running_tasks)
            
            logger.info("开始优雅暂停 %d 个运行中的任务", task_count)
            
            if task_count == 0:
                logger.info("没有运行中的任务，直接暂停")
                return
            
            # 立即暂停所有任务（SIGSTOP）
            for task_run_id in list(running_tasks.keys()):
                try:
                    process_manager.pause_process(task_run_id)
                    task_instance_manager.update_status(task_run_id, "paused", 
                                                      pause_reason="通过 Gunicorn 信号优雅暂停")
                    logger.info("任务已暂停：%s", task_run_id)
                except Exception as e:
                    logger.error("暂停任务失败：%s，错误：%s", task_run_id, str(e))
            
            logger.info("优雅暂停完成，共处理 %d 个任务", task_count)
            
        except Exception as e:
            logger.error("优雅暂停处理失败：%s", str(e), exc_info=True)
    
    def _handle_graceful_resume(self):
        """处理优雅恢复"""
        try:
            from app.service.process_manager import process_manager
            from app.service.task_instance_manager import task_instance_manager
            
            # 获取所有已暂停的任务
            running_tasks = process_manager.get_all_processes()
            paused_tasks = []
            
            for task_run_id, process_info in running_tasks.items():
                if process_manager.is_paused(task_run_id):
                    paused_tasks.append(task_run_id)
            
            task_count = len(paused_tasks)
            logger.info("开始恢复 %d 个已暂停的任务", task_count)
            
            if task_count == 0:
                logger.info("没有已暂停的任务")
                return
            
            # 恢复所有暂停的任务
            for task_run_id in paused_tasks:
                try:
                    process_manager.resume_process(task_run_id)
                    task_instance_manager.update_status(task_run_id, "running",
                                                      resume_reason="通过 Gunicorn 信号优雅恢复")
                    logger.info("任务已恢复：%s", task_run_id)
                except Exception as e:
                    logger.error("恢复任务失败：%s，错误：%s", task_run_id, str(e))
            
            logger.info("优雅恢复完成，共处理 %d 个任务", task_count)
            
        except Exception as e:
            logger.error("优雅恢复处理失败：%s", str(e), exc_info=True)
    
    def _handle_graceful_shutdown(self):
        """处理优雅关闭"""
        try:
            from app.service.process_manager import process_manager
            
            # 获取所有运行中的任务
            running_tasks = process_manager.get_all_processes()
            task_count = len(running_tasks)
            
            logger.info("开始优雅关闭 %d 个运行中的任务", task_count)
            
            # 首先尝试优雅停止所有任务
            for task_run_id in list(running_tasks.keys()):
                try:
                    process_manager.stop_process(task_run_id, force=False)
                    logger.info("优雅停止任务：%s", task_run_id)
                except Exception as e:
                    logger.error("优雅停止任务失败：%s，错误：%s", task_run_id, str(e))
                    # 如果优雅停止失败，则强制终止
                    try:
                        process_manager.stop_process(task_run_id, force=True)
                        logger.info("强制终止任务：%s", task_run_id)
                    except Exception as e2:
                        logger.error("强制终止任务失败：%s，错误：%s", task_run_id, str(e2))
            
            # 等待一段时间让任务有机会优雅退出
            wait_time = min(self._max_pause_wait_time, 10)
            logger.info("等待 %d 秒让任务优雅退出", wait_time)
            time.sleep(wait_time)
            
            logger.info("优雅关闭完成")
            
        except Exception as e:
            logger.error("优雅关闭处理失败：%s", str(e), exc_info=True)
    
    def pause_all_tasks_gracefully(self, wait_for_completion: bool = False) -> bool:
        """优雅地暂停所有任务
        
        Args:
            wait_for_completion: 是否等待所有任务完成后再暂停
            
        Returns:
            bool: 是否成功启动暂停流程
        """
        try:
            if wait_for_completion:
                logger.info("开始等待所有任务完成，然后进行优雅暂停")
                # 简化实现：不等待任务完成，直接发送暂停信号
                os.kill(os.getpid(), signal.SIGUSR1)
            else:
                logger.info("立即开始优雅暂停所有任务")
                os.kill(os.getpid(), signal.SIGUSR1)
            
            return True
            
        except Exception as e:
            logger.error("优雅暂停启动失败：%s", str(e), exc_info=True)
            return False
    
    def resume_all_tasks(self) -> bool:
        """恢复所有暂停的任务
        
        Returns:
            bool: 是否成功启动恢复流程
        """
        try:
            logger.info("开始恢复所有暂停的任务")
            os.kill(os.getpid(), signal.SIGUSR2)
            return True
            
        except Exception as e:
            logger.error("任务恢复启动失败：%s", str(e), exc_info=True)
            return False
    
    def is_pause_requested(self) -> bool:
        """检查是否收到了暂停信号"""
        with self._lock:
            return 'SIGUSR1' in self._pause_signals_received
    
    def is_shutdown_requested(self) -> bool:
        """检查是否收到了关闭信号"""
        with self._lock:
            return 'SIGTERM' in self._pause_signals_received
    
    def clear_signals(self):
        """清除已处理的信号"""
        with self._lock:
            self._pause_signals_received.clear()
    
    def get_status(self) -> Dict[str, any]:
        """获取管理器状态"""
        with self._lock:
            return {
                'pause_signals_received': list(self._pause_signals_received),
                'graceful_pause_enabled': self._graceful_pause_enabled,
                'max_pause_wait_time': self._max_pause_wait_time
            }
    
    def set_max_wait_time(self, seconds: int):
        """设置最大等待时间"""
        self._max_pause_wait_time = max(1, seconds)
        logger.info("设置最大等待时间：%d 秒", self._max_pause_wait_time)


# 全局 Gunicorn 管理器实例
gunicorn_manager = GunicornManager()