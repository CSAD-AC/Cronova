# -*- coding: utf-8 -*-
"""
数据处理服务模块
提供数据处理相关功能
"""
from typing import List, Dict, Any


def process_data(data: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """处理数据列表"""
    return [item for item in data if item.get('enabled', True)]


def transform_data(data: Dict[str, Any]) -> Dict[str, Any]:
    """转换数据格式"""
    return {
        'id': data.get('id', 0),
        'name': str(data.get('name', '')).upper(),
        'value': float(data.get('value', 0))
    }


def validate_data(data: Dict[str, Any]) -> bool:
    """验证数据有效性"""
    required_fields = ['id', 'name']
    return all(field in data for field in required_fields)
