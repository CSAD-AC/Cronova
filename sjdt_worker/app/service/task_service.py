from app.model.task import TaskExecuteMessage
from app.utils.system_utils import SystemUtils
from app.service.task_handler.python_handler import PythonHandler
from app.service.task_handler.java_handler import JavaHandler


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
            print(f"处理任务：{task.taskId}，类型：{task.scriptType}")
            SystemUtils.increment_running_tasks()

            # 获取对应的处理器
            handler = self.handler_map.get(task.scriptType)
            if not handler:
                print(f"不支持的任务类型：{task.scriptType}")
                return False

            # 执行任务
            return handler.handle(task)

        except Exception as e:
            print(f"任务处理失败：{str(e)}")
            return False
        finally:
            SystemUtils.decrement_running_tasks()
            