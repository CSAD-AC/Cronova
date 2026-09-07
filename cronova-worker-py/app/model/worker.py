from pydantic import BaseModel
from typing import List, Optional, Dict


class AKSKValidateResponse(BaseModel):
    code: int
    message: str
    data: Optional[Dict[str, bool]] = None


class WorkerRegisterRequest(BaseModel):
    """Worker注册请求模型（简化版标签）"""
    host: str
    ip: str
    tags: List[str]  # 简化为字符串列表，如 ["V1", "FastAPI", "Python3.11"]


class WorkerRegisterResponse(BaseModel):
    """Worker注册响应模型"""
    host: str
    ip: str
    tags: List[str]
    worker_id: Optional[str] = None


class HeartbeatResponse(BaseModel):
    """心跳响应模型"""
    code: int
    message: str


class StatusReportResponse(BaseModel):
    """状态上报响应模型"""
    code: int
    message: str
    data: Optional[dict] = None
