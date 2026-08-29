# -*- coding: utf-8 -*-
"""
数据库连接模块
用于测试与 MySQL 数据库的连接和查询操作
"""
import os
import json
from typing import List, Dict, Any, Optional
from datetime import datetime


class DatabaseConnection:
    """数据库连接类（模拟）"""

    def __init__(self):
        self.config = self._load_config()
        self.connected = False
        self.connection_info = None

    def _load_config(self) -> Dict[str, str]:
        """从环境变量或配置文件加载数据库配置"""
        return {
            'host': os.getenv('DB_HOST', 'localhost'),
            'port': int(os.getenv('DB_PORT', '3306')),
            'database': os.getenv('DB_DATABASE', 'scheduler'),
            'username': os.getenv('DB_USERNAME', 'root'),
            'password': os.getenv('DB_PASSWORD', '123456'),
        }

    def connect(self) -> bool:
        """模拟数据库连接"""
        print(f"    尝试连接数据库: {self.config['host']}:{self.config['port']}/{self.config['database']}")
        print(f"    用户名: {self.config['username']}")

        # 模拟连接成功
        self.connected = True
        self.connection_info = {
            'server_version': '8.0.42',
            'connection_id': 12345,
            'charset': 'utf8mb4',
            'connected_at': datetime.now().isoformat()
        }
        return True

    def execute_query(self, query: str) -> List[Dict[str, Any]]:
        """模拟执行查询（返回模拟数据）"""
        query_lower = query.lower().strip()

        # 模拟不同查询
        if 'select' in query_lower and 'workers' in query_lower:
            if 'count' in query_lower:
                return [{'worker_count': 1}]
            return [
                {
                    'id': '74cb00be2c93290d8eeb17eeed7f3809',
                    'host': 'docker-desktop',
                    'ip': '192.168.65.3',
                    'status': 'online',
                    'cpu_usage': 0.60,
                    'mem_usage': 5.50,
                    'running_tasks': 0,
                    'last_heartbeat': '2026-03-01 22:37:50'
                }
            ]

        elif 'select' in query_lower and 'tasks' in query_lower:
            if 'count' in query_lower:
                return [{'task_count': 1}]
            return [
                {
                    'id': 'd83d5ba5716a8282d439883132a37bc3',
                    'name': '测试',
                    'execute_type': 'script',
                    'script_type': 'python',
                    'status': 'active',
                    'create_time': '2026-03-01 22:36:33'
                }
            ]

        elif 'select' in query_lower and 'task_run' in query_lower:
            if 'count' in query_lower:
                return [{'run_count': 2}]
            return [
                {
                    'id': '560c57e694af577d81b84e6f2a6fdea0',
                    'task_id': 'd83d5ba5716a8282d439883132a37bc3',
                    'worker_id': '74cb00be2c93290d8eeb17eeed7f3809',
                    'status': 'success',
                    'exit_code': 0,
                    'start_time': '2026-03-01 22:36:35',
                    'end_time': '2026-03-01 22:36:36',
                    'duration_ms': 230
                }
            ]

        elif 'select' in query_lower and 'scripts' in query_lower:
            return [
                {
                    'id': '26b60f9bf737a189804e0e7284276b9f',
                    'name': 'test1',
                    'is_directory': True,
                    'path': 'task-engine/scripts/76d49f5f-e2b0-4a3a-8938-e9c1e4247101/',
                    'create_time': '2026-03-02 06:35:33'
                },
                {
                    'id': '005d3c6684c957078e66748b5001afeb',
                    'name': 'main.py',
                    'is_directory': False,
                    'parent_id': '26b60f9bf737a189804e0e7284276b9f',
                    'create_time': '2026-03-02 06:35:33'
                }
            ]

        elif 'select' in query_lower and 'tags' in query_lower:
            return [
                {'id': 34, 'name': 'V1', 'description': 'Worker注册时自动创建'},
                {'id': 35, 'name': 'Python3.11', 'description': 'Worker注册时自动创建'}
            ]

        elif 'show' in query_lower and 'tables' in query_lower:
            return [
                {'Tables_in_scheduler': 'tasks'},
                {'Tables_in_scheduler': 'task_run'},
                {'Tables_in_scheduler': 'scripts'},
                {'Tables_in_scheduler': 'workers'},
                {'Tables_in_scheduler': 'tags'},
                {'Tables_in_scheduler': 'callback_job_configs'},
                {'Tables_in_scheduler': 'callback_job_runs'},
                {'Tables_in_scheduler': 'users'},
                {'Tables_in_scheduler': 'aksks'},
            ]

        return []

    def get_table_schema(self, table_name: str) -> List[Dict[str, str]]:
        """获取表结构信息"""
        schemas = {
            'workers': [
                {'Field': 'id', 'Type': 'varchar(36)', 'Key': 'PRI', 'Comment': '任务 ID(UUID)'},
                {'Field': 'host', 'Type': 'varchar(64)', 'Key': '', 'Comment': '主机名'},
                {'Field': 'ip', 'Type': 'varchar(32)', 'Key': '', 'Comment': '内网 IP'},
                {'Field': 'status', 'Type': 'varchar(16)', 'Key': '', 'Comment': '状态: online/offline/busy'},
                {'Field': 'cpu_usage', 'Type': 'decimal(5,2)', 'Key': '', 'Comment': 'CPU 使用率 %'},
                {'Field': 'mem_usage', 'Type': 'decimal(5,2)', 'Key': '', 'Comment': '内存使用率 %'},
                {'Field': 'running_tasks', 'Type': 'int', 'Key': '', 'Comment': '当前运行任务数'},
                {'Field': 'last_heartbeat', 'Type': 'timestamp', 'Key': '', 'Comment': '最近一次心跳时间'},
            ],
            'tasks': [
                {'Field': 'id', 'Type': 'varchar(36)', 'Key': 'PRI', 'Comment': '任务 ID(UUID)'},
                {'Field': 'name', 'Type': 'varchar(128)', 'Key': '', 'Comment': '任务名称'},
                {'Field': 'execute_type', 'Type': 'varchar(16)', 'Key': '', 'Comment': '执行类型: script/callback'},
                {'Field': 'script_type', 'Type': 'varchar(16)', 'Key': '', 'Comment': '脚本类型'},
                {'Field': 'script_id', 'Type': 'varchar(36)', 'Key': '', 'Comment': '关联 script.id'},
                {'Field': 'task_type', 'Type': 'varchar(16)', 'Key': '', 'Comment': '任务调度类型'},
                {'Field': 'status', 'Type': 'varchar(16)', 'Key': '', 'Comment': '任务状态'},
            ],
            'scripts': [
                {'Field': 'id', 'Type': 'varchar(36)', 'Key': 'PRI', 'Comment': '主键ID (UUID)'},
                {'Field': 'name', 'Type': 'varchar(100)', 'Key': '', 'Comment': '脚本名称'},
                {'Field': 'path', 'Type': 'varchar(255)', 'Key': '', 'Comment': '脚本路径'},
                {'Field': 'is_directory', 'Type': 'tinyint(1)', 'Key': '', 'Comment': '是否为目录类型'},
                {'Field': 'main_file', 'Type': 'varchar(255)', 'Key': '', 'Comment': '主入口文件路径'},
                {'Field': 'parent_id', 'Type': 'varchar(36)', 'Key': 'MUL', 'Comment': '父脚本ID'},
            ]
        }
        return schemas.get(table_name, [])

    def close(self):
        """关闭数据库连接"""
        self.connected = False
        self.connection_info = None


# 全局数据库连接实例
db_connection = DatabaseConnection()
