import json
import os
from datetime import datetime
from typing import Dict, Optional, Any
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)

class TaskInstanceManager:
    """任务实例管理器，负责持久化任务实例数据"""
    
    def __init__(self, data_dir: str = "task_instances"):
        self.data_dir = data_dir
        self.ensure_data_dir()
    
    def ensure_data_dir(self):
        """确保数据目录存在"""
        if not os.path.exists(self.data_dir):
            os.makedirs(self.data_dir, exist_ok=True)
            logger.info("创建任务实例数据目录：%s", self.data_dir)
    
    def get_instance_file_path(self, task_run_id: str) -> str:
        """获取任务实例文件路径"""
        return os.path.join(self.data_dir, f"{task_run_id}.json")
    
    def save_instance(self, task_run_id: str, instance_data: Dict[str, Any]) -> bool:
        """保存任务实例数据"""
        try:
            file_path = self.get_instance_file_path(task_run_id)
            instance_data['last_updated'] = datetime.utcnow().isoformat() + "Z"
            
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(instance_data, f, indent=2, ensure_ascii=False)
            
            logger.debug("保存任务实例数据成功，taskRunId: %s", task_run_id)
            return True
            
        except Exception as e:
            logger.error("保存任务实例数据失败，taskRunId: %s, 错误: %s", task_run_id, str(e), exc_info=True)
            return False
    
    def load_instance(self, task_run_id: str) -> Optional[Dict[str, Any]]:
        """加载任务实例数据"""
        try:
            file_path = self.get_instance_file_path(task_run_id)
            if not os.path.exists(file_path):
                logger.debug("任务实例文件不存在，taskRunId: %s", task_run_id)
                return None
            
            with open(file_path, 'r', encoding='utf-8') as f:
                instance_data = json.load(f)
            
            logger.debug("加载任务实例数据成功，taskRunId: %s", task_run_id)
            return instance_data
            
        except Exception as e:
            logger.error("加载任务实例数据失败，taskRunId: %s, 错误: %s", task_run_id, str(e), exc_info=True)
            return None
    
    def delete_instance(self, task_run_id: str) -> bool:
        """删除任务实例数据"""
        try:
            file_path = self.get_instance_file_path(task_run_id)
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.info("删除任务实例数据成功，taskRunId: %s", task_run_id)
            return True
            
        except Exception as e:
            logger.error("删除任务实例数据失败，taskRunId: %s, 错误: %s", task_run_id, str(e), exc_info=True)
            return False
    
    def list_instances(self) -> Dict[str, Dict[str, Any]]:
        """列出所有任务实例"""
        instances = {}
        try:
            for filename in os.listdir(self.data_dir):
                if filename.endswith('.json'):
                    task_run_id = filename[:-5]  # 移除.json后缀
                    instance_data = self.load_instance(task_run_id)
                    if instance_data:
                        instances[task_run_id] = instance_data
            
            logger.debug("列出任务实例数量：%d", len(instances))
            return instances
            
        except Exception as e:
            logger.error("列出任务实例失败，错误: %s", str(e), exc_info=True)
            return {}
    
    def create_instance(self, task_run_id: str, task_data: Dict[str, Any]) -> bool:
        """创建新的任务实例"""
        instance_data = {
            "task_run_id": task_run_id,
            "task_id": task_data.get("taskId"),
            "status": "pending",
            "created_time": datetime.utcnow().isoformat() + "Z",
            "start_time": None,
            "end_time": None,
            "pause_time": None,
            "resume_time": None,
            "worker_id": task_data.get("workerId"),
            "script_type": task_data.get("scriptType"),
            "script_content": task_data.get("scriptContent"),
            "temp_script_path": None,
            "log_bucket": None,
            "log_object": None,
            "exit_code": None,
            "error_message": None,
            "duration_ms": None,
            "pause_reason": None,
            "process_id": None
        }
        
        return self.save_instance(task_run_id, instance_data)
    
    def update_status(self, task_run_id: str, status: str, **kwargs) -> bool:
        """更新任务实例状态"""
        instance_data = self.load_instance(task_run_id)
        if not instance_data:
            logger.error("任务实例不存在，无法更新状态，taskRunId: %s", task_run_id)
            return False
        
        instance_data["status"] = status
        
        # 根据状态更新相关字段
        current_time = datetime.utcnow().isoformat() + "Z"
        
        if status == "running":
            instance_data["start_time"] = current_time
        elif status == "paused":
            instance_data["pause_time"] = current_time
            if "pause_reason" in kwargs:
                instance_data["pause_reason"] = kwargs["pause_reason"]
        elif status == "success" or status == "failed" or status == "timeout":
            instance_data["end_time"] = current_time
            if "exit_code" in kwargs:
                instance_data["exit_code"] = kwargs["exit_code"]
            if "error_message" in kwargs:
                instance_data["error_message"] = kwargs["error_message"]
            if "duration_ms" in kwargs:
                instance_data["duration_ms"] = kwargs["duration_ms"]
        elif status == "resumed":
            instance_data["resume_time"] = current_time
        
        # 更新其他字段
        for key, value in kwargs.items():
            if key not in ["pause_reason", "exit_code", "error_message", "duration_ms"]:
                instance_data[key] = value
        
        return self.save_instance(task_run_id, instance_data)
    
    def is_paused(self, task_run_id: str) -> bool:
        """检查任务实例是否已暂停"""
        instance_data = self.load_instance(task_run_id)
        return instance_data and instance_data.get("status") == "paused"
    
    def get_status(self, task_run_id: str) -> Optional[str]:
        """获取任务实例状态"""
        instance_data = self.load_instance(task_run_id)
        return instance_data.get("status") if instance_data else None

# 全局任务实例管理器实例
task_instance_manager = TaskInstanceManager()
