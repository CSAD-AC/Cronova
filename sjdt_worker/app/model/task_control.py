from datetime import datetime
from typing import Optional
from pydantic import BaseModel

class TaskPauseMessage(BaseModel):
    """任务暂停消息模型"""
    taskId: str  # 任务ID
    taskRunId: str  # 任务运行实例ID
    targetWorkerId: Optional[str] = None  # 目标执行器ID
    reason: Optional[str] = None  # 暂停原因
    timestamp: Optional[str] = None  # 暂停时间戳

    class Config:
        extra = "ignore"

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskPauseMessage":
        """从MQ消息体解析为TaskPauseMessage对象"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            raise ValueError(f"转换为TaskPauseMessage失败：{str(e)}")

class TaskResumeMessage(BaseModel):
    """任务恢复消息模型"""
    taskId: str  # 任务ID
    taskRunId: str  # 任务运行实例ID
    targetWorkerId: Optional[str] = None  # 目标执行器ID
    reason: Optional[str] = None  # 恢复原因
    timestamp: Optional[str] = None  # 恢复时间戳

    class Config:
        extra = "ignore"

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskResumeMessage":
        """从MQ消息体解析为TaskResumeMessage对象"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            raise ValueError(f"转换为TaskResumeMessage失败：{str(e)}")

class TaskDeleteMessage(BaseModel):
    """任务删除消息模型"""
    taskId: str  # 任务ID
    taskRunId: str  # 任务运行实例ID
    targetWorkerId: Optional[str] = None  # 目标执行器ID
    reason: Optional[str] = None  # 删除原因
    timestamp: Optional[str] = None  # 删除时间戳

    class Config:
        extra = "ignore"

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskDeleteMessage":
        """从MQ消息体解析为TaskDeleteMessage对象"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            raise ValueError(f"转换为TaskDeleteMessage失败：{str(e)}")

class TaskStopMessage(BaseModel):
    """任务停止消息模型"""
    taskId: str  # 任务ID
    taskRunId: str  # 任务运行实例ID
    targetWorkerId: Optional[str] = None  # 目标执行器ID
    reason: Optional[str] = None  # 停止原因

    class Config:
        extra = "ignore"

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskStopMessage":
        """从MQ消息体解析为TaskStopMessage对象"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            raise ValueError(f"转换为TaskStopMessage失败：{str(e)}")


class WorkerTagUpdateMessage(BaseModel):
    """执行器标签更新消息模型"""
    workerId: str  # 目标执行器ID
    tags: list  # 新的标签列表

    class Config:
        extra = "ignore"

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "WorkerTagUpdateMessage":
        """从MQ消息体解析为WorkerTagUpdateMessage对象"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            raise ValueError(f"转换为WorkerTagUpdateMessage失败：{str(e)}")
