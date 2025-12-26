from app.model.task import TaskExecuteMessage
from app.utils.system_utils import SystemUtils
from app.service.task_handler.python_handler import PythonHandler
from app.service.task_handler.java_handler import JavaHandler
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class TaskService:
    """任务服务类，负责任务分发"""

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
            