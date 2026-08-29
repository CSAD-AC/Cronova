# -*- coding: utf-8 -*-
"""
数据模型模块
定义业务数据模型
"""
from dataclasses import dataclass
from typing import List, Optional
from datetime import datetime


@dataclass
class User:
    """用户模型"""
    id: int
    name: str
    email: str
    role: str = "user"
    created_at: Optional[datetime] = None

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'email': self.email,
            'role': self.role,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


@dataclass
class Order:
    """订单模型"""
    id: int
    user_id: int
    product_name: str
    quantity: int
    price: float
    status: str = "pending"
    created_at: Optional[datetime] = None

    @property
    def total_amount(self) -> float:
        """计算订单总金额"""
        return self.quantity * self.price

    def to_dict(self):
        return {
            'id': self.id,
            'user_id': self.user_id,
            'product_name': self.product_name,
            'quantity': self.quantity,
            'price': self.price,
            'total_amount': self.total_amount,
            'status': self.status,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


@dataclass
class Product:
    """产品模型"""
    id: int
    name: str
    category: str
    price: float
    stock: int
    description: str = ""

    def to_dict(self):
        return {
            'id': self.id,
            'name': self.name,
            'category': self.category,
            'price': self.price,
            'stock': self.stock,
            'description': self.description
        }
