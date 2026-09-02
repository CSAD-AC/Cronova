from pydantic import BaseModel
from typing import Optional, List


class ScriptTag(BaseModel):
    id: Optional[int] = None
    name: Optional[str] = None
    operator: Optional[str] = None
    version: Optional[str] = None


class ScriptInfo(BaseModel):
    id: str
    name: str
    path: str
    is_directory: Optional[bool] = False
    main_file: Optional[str] = None
    parent_id: Optional[str] = None
    root_path: Optional[str] = None
    tags: Optional[List[ScriptTag]] = None

    @classmethod
    def from_dict(cls, data: dict) -> "ScriptInfo":
        """从字典创建ScriptInfo对象，增强健壮性"""
        # 确保tags字段存在且是列表
        if "tags" not in data or data["tags"] is None:
            data["tags"] = []
        elif not isinstance(data["tags"], list):
            data["tags"] = []
        else:
            # 处理tags列表，只保留ScriptTag需要的字段
            processed_tags = []
            for tag_item in data["tags"]:
                if isinstance(tag_item, dict):
                    # 只保留id、name、operator、version字段，忽略其他字段
                    processed_tag = {
                        k: v for k, v in tag_item.items() 
                        if k in ["id", "name", "operator", "version"]
                    }
                    processed_tags.append(processed_tag)
            data["tags"] = processed_tags
        return cls(**data)


class TaskExecuteMessage(BaseModel):
    """任务执行消息模型（对应TASK_EXECUTE主题）"""
    taskId: str  # 任务ID
    taskName: str  # 任务名称
    taskRunId: str = None  # 任务运行实例ID
    description: Optional[str] = None  # 任务描述
    scriptType: str  # 脚本类型（java/python/bash等）
    script: ScriptInfo = None  # 脚本信息
    taskType: str  # 任务类型（once/cron）
    cronExpr: Optional[str] = None  # Cron表达式（仅cron任务使用）
    timeoutSeconds: int = 60  # 超时时间（秒）
    targetWorkerId: Optional[str] = None  # 目标执行器ID（定向任务）
    shardIndex: Optional[int] = None  # 分片序号（从0开始；非分片任务为0）
    shardTotal: Optional[int] = None  # 分片总数（非分片任务为1）
    shardBatchId: Optional[str] = None  # 分片批次ID（同一次触发的所有分片共享）

    class Config:
        extra = "ignore"
        arbitrary_types_allowed = True

    def to_shard_env(self) -> dict:
        """构建分片环境变量字典，供业务脚本感知自己属于哪一片

        业务脚本约定：hash(数据主键) % SJDT_SHARD_TOTAL == SJDT_SHARD_INDEX 的行归本片处理。
        消息未携带分片字段（旧版调度器）时返回空字典，等价于不分片。

        :return: 分片环境变量字典（SJDT_SHARD_INDEX / SJDT_SHARD_TOTAL / SJDT_SHARD_BATCH_ID）
        """
        if self.shardTotal is None:
            return {}
        return {
            "SJDT_SHARD_INDEX": str(self.shardIndex if self.shardIndex is not None else 0),
            "SJDT_SHARD_TOTAL": str(self.shardTotal),
            "SJDT_SHARD_BATCH_ID": str(self.shardBatchId or ""),
        }

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskExecuteMessage":
        """从MQ消息体解析为TaskExecuteMessage对象，增强健壮性"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            
            # 特殊处理script字段，确保ScriptInfo能正确解析
            if "script" in msg_dict and isinstance(msg_dict["script"], dict):
                msg_dict["script"] = ScriptInfo.from_dict(msg_dict["script"])
            
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            # 提供更详细的错误信息，便于调试
            raise ValueError(f"转换为TaskExecuteMessage失败：{str(e)}，消息体：{msg_body[:500]}...")


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


class TaskControlMessage(BaseModel):
    """任务控制消息基类"""
    taskId: str  # 任务ID
    taskRunId: str  # 任务运行实例ID
    targetWorkerId: Optional[str] = None  # 目标执行器ID
    reason: Optional[str] = None  # 控制原因

    class Config:
        extra = "ignore"
        arbitrary_types_allowed = True

    @classmethod
    def from_mq_message(cls, msg_body: str) -> "TaskControlMessage":
        """从MQ消息体解析为TaskControlMessage对象，增强健壮性"""
        import json
        try:
            msg_dict = json.loads(msg_body)
            
            # taskId / taskRunId 强制转为字符串（MQ 消息中可能为数字）
            if "taskId" in msg_dict and not isinstance(msg_dict["taskId"], str):
                msg_dict["taskId"] = str(msg_dict["taskId"])
            if "taskRunId" in msg_dict and not isinstance(msg_dict["taskRunId"], str):
                msg_dict["taskRunId"] = str(msg_dict["taskRunId"])
            
            # 可选字段处理
            if "targetWorkerId" in msg_dict and msg_dict["targetWorkerId"] in [None, ""]:
                msg_dict["targetWorkerId"] = None
            if "reason" in msg_dict and msg_dict["reason"] in [None, ""]:
                msg_dict["reason"] = None
            
            return cls(**msg_dict)
        except json.JSONDecodeError as e:
            raise ValueError(f"MQ消息体JSON解析失败：{str(e)}")
        except Exception as e:
            # 提供更详细的错误信息，便于调试
            raise ValueError(f"转换为TaskControlMessage失败：{str(e)}，消息体：{msg_body[:500]}...")


class TaskStopMessage(TaskControlMessage):
    """任务停止消息模型"""
    pass


class TaskPauseMessage(TaskControlMessage):
    """任务暂停消息模型"""
    pass


class TaskResumeMessage(TaskControlMessage):
    """任务恢复消息模型"""
    pass


class TaskDeleteMessage(TaskControlMessage):
    """任务删除消息模型"""
    pass