from pydantic import BaseModel
from typing import List, Optional, Dict


# AKSK验证响应模型
class AKSKValidateResponse(BaseModel):
    code: int
    message: str
    data: Optional[Dict[str, bool]] = None


# Worker注册请求模型
class TagItem(BaseModel):
    name: str
    version: str



class WorkerRegisterRequest(BaseModel):
    host: str
    ip: str
    tags: List[TagItem]


# Worker注册响应模型
class RegisterTagItem(BaseModel):
    name: str
    version: str


class WorkerRegisterResponse(BaseModel):
    host: str
    ip: str
    tags: List[RegisterTagItem]
    worker_id: Optional[str] = None


# 心跳响应模型
class HeartbeatResponse(BaseModel):
    code: int
    message: str



# 状态上报响应模型
class StatusReportResponse(BaseModel):
    code: int
    message: str
    data: Optional[dict] = None
