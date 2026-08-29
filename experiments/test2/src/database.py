# -*- coding: utf-8 -*-
"""
数据库模拟模块
模拟数据库操作
"""
from typing import List, Dict, Any, Optional
from datetime import datetime
from src.models import User, Order, Product


class Database:
    """模拟数据库类"""

    def __init__(self):
        self._users: Dict[int, User] = {}
        self._orders: Dict[int, Order] = {}
        self._products: Dict[int, Product] = {}
        self._init_sample_data()

    def _init_sample_data(self):
        """初始化示例数据"""
        # 添加示例用户
        users = [
            User(1, "张三", "zhangsan@example.com", "admin"),
            User(2, "李四", "lisi@example.com", "user"),
            User(3, "王五", "wangwu@example.com", "user"),
            User(4, "赵六", "zhaoliu@example.com", "vip"),
        ]
        for user in users:
            self._users[user.id] = user

        # 添加示例产品
        products = [
            Product(1, "iPhone 15", "电子产品", 6999.0, 100, "苹果手机"),
            Product(2, "MacBook Pro", "电子产品", 12999.0, 50, "苹果笔记本电脑"),
            Product(3, "AirPods Pro", "电子产品", 1999.0, 200, "苹果耳机"),
            Product(4, "iPad Air", "电子产品", 4599.0, 80, "苹果平板电脑"),
            Product(5, "华为Mate 60", "电子产品", 5999.0, 120, "华为手机"),
        ]
        for product in products:
            self._products[product.id] = product

        # 添加示例订单
        orders = [
            Order(1, 1, "iPhone 15", 2, 6999.0, "completed"),
            Order(2, 2, "AirPods Pro", 1, 1999.0, "completed"),
            Order(3, 3, "MacBook Pro", 1, 12999.0, "pending"),
        ]
        for order in orders:
            self._orders[order.id] = order

    def get_user_by_id(self, user_id: int) -> Optional[User]:
        """根据ID获取用户"""
        return self._users.get(user_id)

    def get_all_users(self) -> List[User]:
        """获取所有用户"""
        return list(self._users.values())

    def get_orders_by_user_id(self, user_id: int) -> List[Order]:
        """获取用户的订单"""
        return [order for order in self._orders.values() if order.user_id == user_id]

    def get_product_by_id(self, product_id: int) -> Optional[Product]:
        """根据ID获取产品"""
        return self._products.get(product_id)

    def get_all_products(self) -> List[Product]:
        """获取所有产品"""
        return list(self._products.values())

    def get_products_by_category(self, category: str) -> List[Product]:
        """根据分类获取产品"""
        return [p for p in self._products.values() if p.category == category]

    def create_order(self, user_id: int, product_id: int, quantity: int) -> Order:
        """创建订单"""
        product = self.get_product_by_id(product_id)
        if not product:
            raise ValueError(f"产品不存在: {product_id}")

        if product.stock < quantity:
            raise ValueError(f"产品库存不足: {product.name}")

        # 创建订单
        order_id = max(self._orders.keys()) + 1 if self._orders else 1
        order = Order(
            id=order_id,
            user_id=user_id,
            product_name=product.name,
            quantity=quantity,
            price=product.price,
            status="pending",
            created_at=datetime.now()
        )

        # 更新库存
        product.stock -= quantity

        # 保存订单
        self._orders[order_id] = order

        return order

    def update_order_status(self, order_id: int, status: str) -> bool:
        """更新订单状态"""
        if order_id in self._orders:
            self._orders[order_id].status = status
            return True
        return False


# 全局数据库实例
db = Database()
