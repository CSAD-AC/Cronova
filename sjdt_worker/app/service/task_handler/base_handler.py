from abc import ABC, abstractmethod
from app.model.task import TaskExecuteMessage


class BaseHandler(ABC):
    """任务处理器基类"""

    @abstractmethod
    def support_type(self) -> str:
        """返回支持的脚本类型（如python/java/bash）"""
        pass

    @abstractmethod
    def handle(self, task: TaskExecuteMessage) -> bool:
        """处理任务的具体实现"""
        pass
