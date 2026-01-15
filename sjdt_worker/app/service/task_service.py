from app.model.task import TaskExecuteMessage, TaskStopMessage, TaskPauseMessage, TaskResumeMessage, TaskDeleteMessage
from app.utils.system_utils import SystemUtils
from app.service.task_handler.python_handler import PythonHandler
from app.service.task_handler.java_handler import JavaHandler
from app.service.process_manager import process_manager
from app.service.task_instance_manager import task_instance_manager
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class TaskService:
    """任务服务类，负责任务分发和任务控制"""

    def __init__(self):
        # 初始化所有处理器
        self.handlers = [
            PythonHandler(),
            JavaHandler()
        ]

        # 创建类型到处理器的映射
        self.handler_map = {h.support_type(): h for h in self.handlers}

    def process_task(self, task: TaskExecuteMessage) -> bool:
        """处理任务"""
        try:
            logger.info("处理任务：%s，脚本类型：%s", task.taskId, task.scriptType)
            SystemUtils.increment_running_tasks()

            # 获取对应的处理器
            handler = self.handler_map.get(task.scriptType)
            if not handler:
                logger.error("不支持的任务类型：%s", task.scriptType)
                return False

            # 执行任务
            return handler.handle(task)

        except Exception as e:
            logger.error("任务处理失败：%s，错误信息：%s", task.taskId, str(e), exc_info=True)
            return False
        finally:
            SystemUtils.decrement_running_tasks()
            
    def stop_task(self, stop_message: TaskStopMessage) -> bool:
        """停止指定任务"""
        try:
            logger.info("停止任务：%s", stop_message.taskRunId)
            
            # 检查任务是否在运行
            if not process_manager.is_running(stop_message.taskRunId):
                logger.warning("任务未在运行：%s", stop_message.taskRunId)
                return True  # 任务已经停止，认为成功
            
            # 停止进程
            success = process_manager.stop_process(stop_message.taskRunId)
            
            if success:
                # 更新任务实例状态
                task_instance_manager.update_status(
                    stop_message.taskRunId, 
                    "failed",
                    error_message=f"任务被手动停止，原因：{stop_message.reason}"
                )
                
                # 立即上报任务状态到调度器
                from app.service.task_status_service import task_status_service
                task_status_service.report_task_status()
                
                logger.info("任务停止成功：%s", stop_message.taskRunId)
            else:
                logger.error("任务停止失败：%s", stop_message.taskRunId)
            
            return success
            
        except Exception as e:
            logger.error("停止任务异常：%s，错误信息：%s", stop_message.taskRunId, str(e), exc_info=True)
            return False
    
    def pause_task(self, pause_message: TaskPauseMessage) -> bool:
        """暂停指定任务"""
        try:
            logger.info("暂停任务：%s", pause_message.taskRunId)
            
            # 检查任务是否在运行
            if not process_manager.is_running(pause_message.taskRunId):
                logger.warning("任务未在运行：%s", pause_message.taskRunId)
                return True
            
            # 更新任务实例状态为暂停
            task_instance_manager.update_status(
                pause_message.taskRunId, 
                "paused",
                pause_reason=pause_message.reason
            )
            
            logger.info("任务暂停成功：%s", pause_message.taskRunId)
            return True
            
        except Exception as e:
            logger.error("暂停任务异常：%s，错误信息：%s", pause_message.taskRunId, str(e), exc_info=True)
            return False
    
    def resume_task(self, resume_message: TaskResumeMessage) -> bool:
        """恢复指定任务"""
        try:
            logger.info("恢复任务：%s", resume_message.taskRunId)
            
            # 检查任务实例是否存在
            if not task_instance_manager.get_status(resume_message.taskRunId):
                logger.warning("任务实例不存在：%s", resume_message.taskRunId)
                return False
            
            # 更新任务实例状态为恢复运行
            task_instance_manager.update_status(
                resume_message.taskRunId, 
                "running"
            )
            
            logger.info("任务恢复成功：%s", resume_message.taskRunId)
            return True
            
        except Exception as e:
            logger.error("恢复任务异常：%s，错误信息：%s", resume_message.taskRunId, str(e), exc_info=True)
            return False
    
    def delete_task(self, delete_message: TaskDeleteMessage) -> bool:
        """删除指定任务"""
        try:
            logger.info("删除任务：%s", delete_message.taskRunId)
            
            # 如果任务正在运行，先停止它
            if process_manager.is_running(delete_message.taskRunId):
                process_manager.stop_process(delete_message.taskRunId)
            
            # 删除任务实例数据
            success = task_instance_manager.delete_instance(delete_message.taskRunId)
            
            if success:
                logger.info("任务删除成功：%s", delete_message.taskRunId)
            else:
                logger.error("任务删除失败：%s", delete_message.taskRunId)
            
            return success
            
        except Exception as e:
            logger.error("删除任务异常：%s，错误信息：%s", delete_message.taskRunId, str(e), exc_info=True)
            return False
            