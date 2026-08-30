from app.model.task import TaskExecuteMessage, TaskStopMessage, TaskPauseMessage, TaskResumeMessage, TaskDeleteMessage
from app.utils.system_utils import SystemUtils
from app.service.task_handler.python_handler import PythonHandler
from app.service.task_handler.java_handler import JavaHandler
from app.service.task_handler.go_handler import GoHandler
from app.service.process_manager import process_manager
from app.service.task_instance_manager import task_instance_manager
from app.service.task_status_service import task_status_service
from app.service.gunicorn_manager import gunicorn_manager
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


"""
 Gunicorn 任务控制机制说明：

 本服务采用Gunicorn停止机制来确保任务控制的完整性和可靠性：

 1. **正常停止机制**：
    - 停止时，Gunicorn会等待当前正在处理的请求完成后再停止服务
    - 这确保了所有已接收的任务请求都能得到完整处理
    - 超时时间可配置（默认30秒），超时后强制终止

 2. **强制终止机制**：
    - 强制终止时，立即停止所有任务进程，不等待
    - 使用场景：系统紧急关闭或任务出现异常情况

 3. **批量操作**：
    - pause_all_tasks(): 暂停所有任务
    - shutdown_all_tasks(): 关闭所有任务
    - resume_all_tasks(): 恢复所有暂停的任务

 4. **信号处理**：
   - SIGTERM: 关闭
   - SIGUSR1: 暂停
   - SIGUSR2: 恢复任务
   - SIGCONT: 继续进程

 这种机制确保了任务控制的安全性和可靠性，避免了任务中断导致的数据丢失或状态不一致。
 """


class TaskService:
    """任务服务类，负责任务分发和任务控制"""

    def __init__(self):
        self.handlers = [
            PythonHandler(),
            JavaHandler(),
            GoHandler()
        ]
        self.handler_map = {h.support_type(): h for h in self.handlers}

    def process_task(self, task: TaskExecuteMessage) -> bool:
        """处理任务"""
        try:
            logger.info("处理任务：%s，脚本类型：%s", task.taskId, task.scriptType)
            SystemUtils.increment_running_tasks()

            handler = self.handler_map.get(task.scriptType)
            if not handler:
                logger.error("不支持的任务类型：%s", task.scriptType)
                return False

            return handler.handle(task)

        except Exception as e:
            logger.error("任务处理失败：%s，错误信息：%s", task.taskId, str(e), exc_info=True)
            return False
        finally:
            SystemUtils.decrement_running_tasks()
            
    def stop_task(self, stop_message: TaskStopMessage) -> bool:
        """停止指定任务
        
        Args:
            stop_message: 停止任务消息
        """
        try:
            # 获取操作前状态
            before_status = task_instance_manager.get_status(stop_message.taskRunId)
            is_process_running = process_manager.is_running(stop_message.taskRunId)
            
            logger.info("开始停止任务，Gunicorn机制，taskRunId：%s，操作前状态：%s，进程运行状态：%s",
                       stop_message.taskRunId, 
                       before_status or 'unknown', 
                       is_process_running)
            
            if not is_process_running:
                logger.warning("任务未在运行，taskRunId：%s，操作前状态：%s", stop_message.taskRunId, before_status or 'unknown')
                return True
            
            # 使用Gunicorn停止机制
            logger.info("通过Gunicorn管理器停止任务，taskRunId：%s，确保请求完整处理", stop_message.taskRunId)
            
            # 1. 首先设置Gunicorn接收关闭信号，但不立即执行
            gunicorn_manager.set_max_wait_time(30)  # 设置最大等待时间30秒
            
            # 2. 正常停止该任务进程
            # 修改：如果是超时强制停止，应该使用force=True，否则会等待进程自然结束，导致超时停止失败
            # 这里的 reason 是 "任务执行超时，系统强制停止"
            is_timeout_stop = stop_message.reason and "超时" in stop_message.reason
            force_stop = is_timeout_stop
            
            if force_stop:
                logger.info("检测到超时停止请求，将使用强制停止模式，taskRunId：%s", stop_message.taskRunId)
            
            success = process_manager.stop_process(stop_message.taskRunId, force=force_stop)
            
            if success:
                logger.info("Gunicorn停止成功，taskRunId：%s", stop_message.taskRunId)
                
                # 获取操作后状态
                after_status = task_instance_manager.get_status(stop_message.taskRunId)
                logger.info("任务停止完成，taskRunId：%s，操作后状态：%s，原因：%s", 
                          stop_message.taskRunId, 
                          after_status or 'unknown',
                          stop_message.reason)
                
                # 更新任务状态
                task_instance_manager.update_status(
                    stop_message.taskRunId, 
                    "failed",
                    error_message=f"任务被Gunicorn停止，原因：{stop_message.reason}"
                )
                task_status_service.report_task_status()
            else:
                logger.error("Gunicorn停止失败，taskRunId：%s，操作前状态：%s，原因：%s", 
                           stop_message.taskRunId, 
                           before_status or 'unknown',
                           stop_message.reason)
            
            return success
            
        except Exception as e:
            logger.error("停止任务异常，taskRunId：%s，错误信息：%s", stop_message.taskRunId, str(e), exc_info=True)
            return False
    
    def pause_task(self, pause_message: TaskPauseMessage) -> bool:
        """暂停指定任务"""
        try:
            # 获取操作前状态
            before_status = task_instance_manager.get_status(pause_message.taskRunId)
            is_process_running = process_manager.is_running(pause_message.taskRunId)
            is_already_paused = process_manager.is_paused(pause_message.taskRunId)
            
            logger.info("开始暂停任务，taskRunId：%s，操作前状态：%s，进程运行状态：%s，已暂停状态：%s，原因：%s",
                       pause_message.taskRunId, 
                       before_status or 'unknown', 
                       is_process_running,
                       is_already_paused,
                       pause_message.reason)
            
            if not is_process_running:
                logger.warning("任务进程不存在或已停止，taskRunId：%s，操作前状态：%s", 
                             pause_message.taskRunId, before_status or 'unknown')
                return False
            
            if is_already_paused:
                logger.info("任务已处于暂停状态，跳过暂停操作，taskRunId：%s", pause_message.taskRunId)
                return True
            
            success = process_manager.pause_process(pause_message.taskRunId)
            
            if success:
                # 先更新任务实例状态
                task_instance_manager.update_status(
                    pause_message.taskRunId, 
                    "paused",
                    pause_reason=pause_message.reason
                )
                
                # 获取操作后状态（状态更新后）
                after_status = task_instance_manager.get_status(pause_message.taskRunId)
                logger.info("任务暂停成功，taskRunId：%s，操作后状态：%s，原因：%s", 
                           pause_message.taskRunId, 
                           after_status or 'unknown',
                           pause_message.reason)
                
                task_status_service.report_task_status()
            else:
                logger.error("任务暂停失败，taskRunId：%s，操作前状态：%s，原因：%s", 
                           pause_message.taskRunId, 
                           before_status or 'unknown',
                           pause_message.reason)
            
            return success
            
        except Exception as e:
            logger.error("暂停任务异常，taskRunId：%s，错误信息：%s", pause_message.taskRunId, str(e), exc_info=True)
            return False
    
    def pause_all_tasks(self, wait_for_completion: bool = False) -> bool:
        """暂停所有运行中的任务
        
        Args:
            wait_for_completion: 是否等待所有任务完成后再暂停
            
        Returns:
            bool: 是否成功启动暂停流程
        """
        try:
            logger.info("启动Gunicorn暂停所有任务，等待完成模式：%s", wait_for_completion)
            
            # 使用Gunicorn管理器执行暂停
            success = gunicorn_manager.pause_all_tasks_gracefully(wait_for_completion)
            
            if success:
                logger.info("Gunicorn暂停流程已启动，将确保所有已接收的请求完整处理")
                task_status_service.report_task_status()
            else:
                logger.error("Gunicorn暂停流程启动失败")
            
            return success
            
        except Exception as e:
            logger.error("Gunicorn暂停任务异常：%s", str(e), exc_info=True)
            return False
    
    def resume_all_tasks(self) -> bool:
        """恢复所有暂停的任务
        
        Returns:
            bool: 是否成功启动恢复流程
        """
        try:
            logger.info("启动恢复所有暂停的任务")
            
            success = gunicorn_manager.resume_all_tasks()
            
            if success:
                logger.info("任务恢复流程已启动")
                task_status_service.report_task_status()
            else:
                logger.error("任务恢复流程启动失败")
            
            return success
            
        except Exception as e:
            logger.error("恢复任务异常：%s", str(e), exc_info=True)
            return False
    
    def resume_task(self, resume_message: TaskResumeMessage) -> bool:
        """恢复指定任务"""
        try:
            # 获取操作前状态
            before_status = task_instance_manager.get_status(resume_message.taskRunId)
            is_process_running = process_manager.is_running(resume_message.taskRunId)
            is_paused = process_manager.is_paused(resume_message.taskRunId)
            
            logger.info("开始恢复任务，taskRunId：%s，操作前状态：%s，进程运行状态：%s，暂停状态：%s",
                       resume_message.taskRunId, 
                       before_status or 'unknown', 
                       is_process_running,
                       is_paused)
            
            if not before_status:
                logger.warning("任务实例不存在，taskRunId：%s", resume_message.taskRunId)
                return False
            
            if not is_process_running:
                logger.warning("任务进程不存在或已停止，taskRunId：%s，操作前状态：%s", 
                             resume_message.taskRunId, before_status or 'unknown')
                return False
            
            if not is_paused:
                logger.info("任务未处于暂停状态，跳过恢复操作，taskRunId：%s，操作前状态：%s", 
                           resume_message.taskRunId, before_status or 'unknown')
                return True
            
            success = process_manager.resume_process(resume_message.taskRunId)
            
            if success:
                # 先更新任务实例状态
                task_instance_manager.update_status(resume_message.taskRunId, "running")
                
                # 获取操作后状态（状态更新后）
                after_status = task_instance_manager.get_status(resume_message.taskRunId)
                logger.info("任务恢复成功，taskRunId：%s，操作后状态：%s", 
                           resume_message.taskRunId, 
                           after_status or 'unknown')
                
                task_status_service.report_task_status()
            else:
                logger.error("任务恢复失败，taskRunId：%s，操作前状态：%s，暂停状态：%s", 
                           resume_message.taskRunId, 
                           before_status or 'unknown',
                           is_paused)
            
            return success
            
        except Exception as e:
            logger.error("恢复任务异常，taskRunId：%s，错误信息：%s", resume_message.taskRunId, str(e), exc_info=True)
            return False
    
    def kill_task(self, kill_message: TaskStopMessage) -> bool:
        """强制终止指定任务（通过Gunicorn机制立即终止）"""
        try:
            # 获取操作前状态
            before_status = task_instance_manager.get_status(kill_message.taskRunId)
            is_process_running = process_manager.is_running(kill_message.taskRunId)
            
            logger.info("开始强制终止任务，Gunicorn机制，taskRunId：%s，操作前状态：%s，进程运行状态：%s，原因：%s",
                       kill_message.taskRunId, 
                       before_status or 'unknown', 
                       is_process_running,
                       kill_message.reason)
            
            if not is_process_running:
                logger.warning("任务未在运行，跳过强制终止，taskRunId：%s，操作前状态：%s", 
                             kill_message.taskRunId, before_status or 'unknown')
                return True
            
            # 使用Gunicorn强制终止机制
            logger.info("通过Gunicorn管理器执行强制终止，taskRunId：%s，立即终止处理，不等待", kill_message.taskRunId)
            
            # 强制终止立即执行，不等待
            success = process_manager.stop_process(kill_message.taskRunId, force=True)
            
            if success:
                logger.info("Gunicorn强制终止成功，taskRunId：%s", kill_message.taskRunId)
                
                # 先更新任务实例状态
                task_instance_manager.update_status(
                    kill_message.taskRunId, 
                    "failed",
                    error_message=f"任务被Gunicorn强制终止，原因：{kill_message.reason}"
                )
                
                # 获取操作后状态（状态更新后）
                after_status = task_instance_manager.get_status(kill_message.taskRunId)
                logger.info("任务强制终止完成，taskRunId：%s，操作后状态：%s，原因：%s", 
                          kill_message.taskRunId, 
                          after_status or 'unknown',
                          kill_message.reason)
                
                task_status_service.report_task_status()
            else:
                logger.error("Gunicorn强制终止失败，taskRunId：%s，操作前状态：%s，原因：%s", 
                           kill_message.taskRunId, 
                           before_status or 'unknown',
                           kill_message.reason)
            
            return success
            
        except Exception as e:
            logger.error("强制终止任务异常，taskRunId：%s，错误信息：%s", kill_message.taskRunId, str(e), exc_info=True)
            return False
    
    def shutdown_all_tasks(self) -> bool:
        """关闭所有运行中的任务
        
        Returns:
            bool: 是否成功启动关闭流程
        """
        try:
            logger.info("启动Gunicorn关闭所有任务")
            
            # 检查是否已经收到了关闭信号
            if gunicorn_manager.is_shutdown_requested():
                logger.info("Gunicorn已经收到关闭信号，正在处理中")
                return True
            
            # 使用Gunicorn管理器执行关闭
            success = gunicorn_manager._handle_graceful_shutdown()
            
            if success:
                logger.info("Gunicorn关闭流程已启动，将确保所有已接收的请求完整处理")
                task_status_service.report_task_status()
            else:
                logger.error("Gunicorn关闭流程启动失败")
            
            return success
            
        except Exception as e:
            logger.error("Gunicorn关闭任务异常：%s", str(e), exc_info=True)
            return False
    
    def get_gunicorn_status(self) -> dict:
        """获取 Gunicorn 管理器状态
        
        Returns:
            dict: Gunicorn 管理器状态信息
        """
        try:
            return gunicorn_manager.get_status()
        except Exception as e:
            logger.error("获取 Gunicorn 状态失败：%s", str(e))
            return {}
    
    def delete_task(self, delete_message: TaskDeleteMessage) -> bool:
        """删除指定任务"""
        try:
            logger.info("删除任务：%s", delete_message.taskRunId)
            
            if process_manager.is_running(delete_message.taskRunId):
                process_manager.stop_process(delete_message.taskRunId)
            
            success = task_instance_manager.delete_instance(delete_message.taskRunId)
            
            if success:
                logger.info("任务删除成功：%s", delete_message.taskRunId)
            else:
                logger.error("任务删除失败：%s", delete_message.taskRunId)
            
            return success
            
        except Exception as e:
            logger.error("删除任务异常：%s，错误信息：%s", delete_message.taskRunId, str(e), exc_info=True)
            return False
