# -*- coding: utf-8 -*-
"""
用户服务模块
处理用户相关业务逻辑
"""
from typing import List, Optional, Dict, Any
from src.database import db
from src.models import User


class UserService:
    """用户服务类"""

    def get_user_by_id(self, user_id: int) -> Optional[Dict[str, Any]]:
        """获取用户信息"""
        user = db.get_user_by_id(user_id)
        if user:
            return user.to_dict()
        return None

    def get_all_users(self) -> List[Dict[str, Any]]:
        """获取所有用户列表"""
        users = db.get_all_users()
        return [user.to_dict() for user in users]

    def get_user_statistics(self, user_id: int) -> Dict[str, Any]:
        """获取用户统计信息"""
        user = db.get_user_by_id(user_id)
        if not user:
            return {}

        orders = db.get_orders_by_user_id(user_id)

        total_spent = sum(order.total_amount for order in orders)
        completed_orders = sum(1 for order in orders if order.status == "completed")
        pending_orders = sum(1 for order in orders if order.status == "pending")

        return {
            'user': user.to_dict(),
            'total_orders': len(orders),
            'completed_orders': completed_orders,
            'pending_orders': pending_orders,
            'total_spent': total_spent,
            'average_order_amount': total_spent / len(orders) if orders else 0
        }

    def validate_user(self, user_id: int) -> bool:
        """验证用户是否存在"""
        return db.get_user_by_id(user_id) is not None


# 全局用户服务实例
user_service = UserService()
