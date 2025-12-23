from pydantic import BaseModel
from typing import Optional, List


class ScriptTag(BaseModel):
    id: int
    name: str
    operator: str
    version: str


class ScriptInfo(BaseModel):
    id: str
    name: str
    path: str
    tags: List[ScriptTag]


class TaskExecuteMessage(BaseModel):
    """任务执行消息模型（对应TASK_EXECUTE主题）"""
    taskId: str  # 任务ID
    taskName: str  # 任务名称
    taskRunId: str  # 任务运行实例ID
    description: Optional[str] = None  # 任务描述
    scriptType: str  # 脚本类型（java/python/bash等）
    script: ScriptInfo  # 脚本信息
    taskType: str  # 任务类型（once/cron）
    timeoutSeconds: int  # 超时时间（秒）
    targetWorkerId: Optional[str] = None  # 目标执行器ID（定向任务）

    class Config:
        extra = "ignore"

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskExecuteMessage":
        """从MQ消息体解析为TaskExecuteMessage对象"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            return cls(** msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            raise ValueError(f"转换为TaskExecuteMessage失败：{str(e)}")


class TaskResultMessage(BaseModel):
    """任务结果消息模型（对应TASK_RESULT主题）"""
    workerId: str  # 执行器ID
    taskId: str  # 任务ID
    taskRunId: str  # 任务运行实例ID
    status: str  # 执行状态（pending/running/success/failed/timeout）
    exitCode: int  # 退出码
    startTime: str  # 开始时间
    endTime: str  # 结束时间
    durationMs: int  # 执行耗时（毫秒）
    errorMessage: Optional[str] = None  # 错误信息（可选）
    logPath: Optional[str] = None  # MinIO日志路径

    def to_json(self) -> str:
        """转换为JSON字符串"""
        return self.model_dump_json(ensure_ascii=False)