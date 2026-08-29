# -*- coding: utf-8 -*-
"""
工具模块
提供通用的辅助函数
"""


def get_config_value(key: str, default=None):
    """获取配置值"""
    configs = {
        'debug': True,
        'port': 8080,
        'host': 'localhost'
    }
    return configs.get(key, default)


def format_message(message: str) -> str:
    """格式化消息"""
    return f"[INFO] {message}"
