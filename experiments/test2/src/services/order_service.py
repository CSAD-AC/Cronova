# -*- coding: utf-8 -*-
"""
订单服务模块
处理订单相关业务逻辑
"""
from typing import List, Dict, Any, Optional
from datetime import datetime
from src.database import db
from src.models import Order
from src.services.user_service import user_service


class OrderService:
    """订单服务类"""

    def create_order(self, user_id: int, product_id: int, quantity: int) -> Dict[str, Any]:
        """创建订单"""
        # 验证用户
        if not user_service.validate_user(user_id):
            raise ValueError(f"用户不存在: {user_id}")

        # 验证产品
        product = db.get_product_by_id(product_id)
        if not product:
            raise ValueError(f"产品不存在: {product_id}")

        # 创建订单
        order = db.create_order(user_id, product_id, quantity)

        return {
            'success': True,
            'order': order.to_dict(),
            'message': f"订单创建成功，总金额: ¥{order.total_amount:.2f}"
        }

    def get_user_orders(self, user_id: int) -> List[Dict[str, Any]]:
        """获取用户的所有订单"""
        if not user_service.validate_user(user_id):
            return []

        orders = db.get_orders_by_user_id(user_id)
        return [order.to_dict() for order in orders]

    def get_order_by_id(self, order_id: int) -> Optional[Dict[str, Any]]:
        """根据ID获取订单"""
        for order in db._orders.values():
            if order.id == order_id:
                return order.to_dict()
        return None

    def update_order_status(self, order_id: int, status: str) -> Dict[str, Any]:
        """更新订单状态"""
        valid_statuses = ["pending", "processing", "completed", "cancelled"]
        if status not in valid_statuses:
            raise ValueError(f"无效的订单状态: {status}")

        success = db.update_order_status(order_id, status)
        if success:
            order = self.get_order_by_id(order_id)
            return {
                'success': True,
                'order': order,
                'message': f"订单状态已更新为: {status}"
            }
        return {
            'success': False,
            'message': f"订单不存在: {order_id}"
        }

    def get_all_orders(self) -> List[Dict[str, Any]]:
        """获取所有订单"""
        return [order.to_dict() for order in db._orders.values()]

    def get_order_statistics(self) -> Dict[str, Any]:
        """获取订单统计信息"""
        all_orders = list(db._orders.values())

        total_orders = len(all_orders)
        completed_orders = sum(1 for o in all_orders if o.status == "completed")
        pending_orders = sum(1 for o in all_orders if o.status == "pending")

        total_revenue = sum(o.total_amount for o in all_orders if o.status == "completed")
        average_amount = total_revenue / completed_orders if completed_orders > 0 else 0

        return {
            'total_orders': total_orders,
            'completed_orders': completed_orders,
            'pending_orders': pending_orders,
            'total_revenue': total_revenue,
            'average_order_amount': average_amount
        }


# 全局订单服务实例
order_service = OrderService()
