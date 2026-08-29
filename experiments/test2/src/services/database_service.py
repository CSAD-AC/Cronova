# -*- coding: utf-8 -*-
"""
数据库服务模块
提供业务层的数据库查询和操作
"""
from typing import List, Dict, Any, Optional
from src.db_connection import db_connection


class DatabaseService:
    """数据库服务类"""

    def get_worker_stats(self) -> Dict[str, Any]:
        """获取 Worker 统计信息"""
        workers = db_connection.execute_query("SELECT * FROM workers WHERE status = 'online'")
        total = len(workers)

        cpu_sum = sum(float(w.get('cpu_usage', 0)) for w in workers)
        mem_sum = sum(float(w.get('mem_usage', 0)) for w in workers)

        return {
            'total_workers': total,
            'online_workers': total,
            'offline_workers': 0,
            'avg_cpu_usage': cpu_sum / total if total > 0 else 0,
            'avg_mem_usage': mem_sum / total if total > 0 else 0
        }

    def get_task_stats(self) -> Dict[str, Any]:
        """获取任务统计信息"""
        tasks = db_connection.execute_query("SELECT * FROM tasks WHERE status = 'active'")
        total = len(tasks)

        script_tasks = sum(1 for t in tasks if t.get('execute_type') == 'script')
        callback_tasks = sum(1 for t in tasks if t.get('execute_type') == 'callback')

        return {
            'total_tasks': total,
            'active_tasks': total,
            'script_tasks': script_tasks,
            'callback_tasks': callback_tasks
        }

    def get_recent_task_runs(self, limit: int = 10) -> List[Dict[str, Any]]:
        """获取最近的任务执行记录"""
        query = f"SELECT * FROM task_run ORDER BY create_time DESC LIMIT {limit}"
        return db_connection.execute_query(query)

    def get_task_run_by_id(self, task_run_id: str) -> Optional[Dict[str, Any]]:
        """根据 ID 获取任务执行记录"""
        query = f"SELECT * FROM task_run WHERE id = '{task_run_id}'"
        results = db_connection.execute_query(query)
        return results[0] if results else None

    def get_worker_by_id(self, worker_id: str) -> Optional[Dict[str, Any]]:
        """根据 ID 获取 Worker 信息"""
        query = f"SELECT * FROM workers WHERE id = '{worker_id}'"
        results = db_connection.execute_query(query)
        return results[0] if results else None

    def get_all_scripts(self) -> List[Dict[str, Any]]:
        """获取所有脚本"""
        return db_connection.execute_query("SELECT * FROM scripts ORDER BY create_time DESC")

    def get_directory_scripts(self) -> List[Dict[str, Any]]:
        """获取所有目录类型的脚本"""
        return db_connection.execute_query("SELECT * FROM scripts WHERE is_directory = 1")

    def get_scripts_by_parent(self, parent_id: str) -> List[Dict[str, Any]]:
        """根据父 ID 获取子脚本"""
        query = f"SELECT * FROM scripts WHERE parent_id = '{parent_id}'"
        return db_connection.execute_query(query)

    def get_task_by_id(self, task_id: str) -> Optional[Dict[str, Any]]:
        """根据 ID 获取任务"""
        query = f"SELECT * FROM tasks WHERE id = '{task_id}'"
        results = db_connection.execute_query(query)
        return results[0] if results else None

    def get_all_tags(self) -> List[Dict[str, Any]]:
        """获取所有标签"""
        return db_connection.execute_query("SELECT * FROM tags ORDER BY name")

    def execute_custom_query(self, query: str) -> List[Dict[str, Any]]:
        """执行自定义 SQL 查询"""
        return db_connection.execute_query(query)


# 全局数据库服务实例
database_service = DatabaseService()
