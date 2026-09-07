import time
from datetime import datetime, timedelta
from typing import Dict
from app.config import Config
from app.service.process_manager import process_manager
from app.http_client import HttpClient
from app.model.task import TaskResultMessage
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)

# 任务状态报告服务
class TaskStatusService:
    def __init__(self):
        self._task_start_time: Dict[str, datetime] = {}  # 记录每个任务的开始时间
        self._http_client = HttpClient()
        
    def report_task_status(self):
        """报告所有正在运行的任务状态"""
        logger.debug("开始报告任务状态")
        
        # 获取所有正在运行的进程
        all_processes = process_manager.get_all_processes()
        
        for task_run_id, process_info in all_processes.items():
            # 记录任务开始时间
            if task_run_id not in self._task_start_time:
                self._task_start_time[task_run_id] = process_info['start_time']
                logger.info("记录任务开始时间，taskRunId: %s, 开始时间: %s", task_run_id, process_info['start_time'])
            
            # 检查任务运行时间
            current_time = datetime.utcnow()
            elapsed_seconds = (current_time - process_info['start_time']).total_seconds()
            
            # 前一分钟内频率为3s一次，一分钟之后频率为60s一次
            if elapsed_seconds < 60:
                last_report_time = self._get_last_report_time(task_run_id)
                if (current_time - last_report_time).total_seconds() >= 3:
                    self._report_status_with_retry(task_run_id, process_info, process_info['start_time'])
            else:
                last_report_time = self._get_last_report_time(task_run_id)
                if (current_time - last_report_time).total_seconds() >= 60:
                    self._report_status_with_retry(task_run_id, process_info, process_info['start_time'])
    
    def _get_last_report_time(self, task_run_id: str) -> datetime:
        """获取上次报告时间，默认返回任务开始时间"""
        return getattr(self, f"_last_report_{task_run_id}", self._task_start_time.get(task_run_id, datetime.utcnow()))
    
    def _set_last_report_time(self, task_run_id: str, time: datetime):
        """设置上次报告时间"""
        setattr(self, f"_last_report_{task_run_id}", time)
    

    
    def _report_status(self, task_run_id: str, process_info: Dict, start_time: datetime):
        """报告单个任务状态"""
        logger.info("报告任务状态，taskRunId: %s", task_run_id)
        
        # 获取任务的真实状态
        from app.service.task_instance_manager import task_instance_manager
        current_status = task_instance_manager.get_status(task_run_id)
        
        # 如果任务实例不存在，回退到running状态
        if not current_status:
            current_status = "running"
            logger.warning("任务实例不存在，使用默认状态running，taskRunId: %s", task_run_id)
        
        # 构建任务结果消息
        current_time = datetime.utcnow()
        # 构建完整的日志路径
        log_path = f"{process_info['log_bucket']}/{process_info['log_object']}"
        result = TaskResultMessage(
            workerId=Config.WORKER_ID,
            taskId=process_info['task'].taskId,
            taskRunId=task_run_id,
            status=current_status,
            exitCode=0,
            startTime=start_time.isoformat() + "Z",
            endTime="",
            durationMs=int((current_time - start_time).total_seconds() * 1000),
            errorMessage=None,
            logPath=log_path
        )
        
        logger.debug("状态报告详细信息，taskRunId: %s，状态: %s", task_run_id, current_status)
        
        # 发送状态报告
        try:
            # 调用调度器的任务状态报告接口 - 以JAVA侧接口为准
            url = f"{Config.SCHEDULER_BASE_URL}/api/worker/task-status/{task_run_id}"
            response = self._http_client.post(url, json_data=result.model_dump())
            if response:
                # 检查业务响应码
                response_code = response.get("code")
                if response_code == 200:
                    logger.info("状态报告发送成功，taskRunId: %s，状态: %s，耗时: %dms", 
                              task_run_id, result.status, result.durationMs)
                    # 更新上次报告时间
                    self._set_last_report_time(task_run_id, current_time)
                else:
                    # 业务响应码非200，视为失败
                    error_msg = response.get("message", "未知错误")
                    logger.error("状态报告业务处理失败，taskRunId: %s，状态: %s，业务码: %d，错误信息: %s", 
                               task_run_id, result.status, response_code, error_msg)
                    raise Exception(f"业务处理失败: code={response_code}, message={error_msg}")
            else:
                logger.error("状态报告发送失败，taskRunId: %s，状态: %s", task_run_id, result.status)
                raise Exception("状态报告HTTP响应为空")
        except Exception as e:
            logger.error("状态报告发送异常，taskRunId: %s，状态: %s，错误: %s", 
                       task_run_id, result.status, str(e), exc_info=True)
            raise  # 重新抛出异常以触发重试
    
    def _report_status_with_retry(self, task_run_id: str, process_info: Dict, start_time: datetime, max_retries: int = 3):
        """带重试机制的状态报告"""
        for attempt in range(max_retries):
            try:
                self._report_status(task_run_id, process_info, start_time)
                return
            except Exception as e:
                logger.error("状态报告失败（尝试 %d/%d），taskRunId: %s，错误: %s", 
                           attempt + 1, max_retries, task_run_id, str(e))
                if attempt < max_retries - 1:
                    time.sleep(2 ** attempt)  # 指数退避
                else:
                    logger.error("状态报告最终失败，taskRunId: %s", task_run_id)
                    raise  # 最终失败时抛出异常

# 全局任务状态报告服务实例
task_status_service = TaskStatusService()