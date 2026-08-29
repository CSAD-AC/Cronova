# -*- coding: utf-8 -*-
"""
产品服务模块
处理产品相关业务逻辑
"""
from typing import List, Dict, Any, Optional
from src.database import db
from src.models import Product


class ProductService:
    """产品服务类"""

    def get_product_by_id(self, product_id: int) -> Optional[Dict[str, Any]]:
        """根据ID获取产品"""
        product = db.get_product_by_id(product_id)
        if product:
            return product.to_dict()
        return None

    def get_all_products(self) -> List[Dict[str, Any]]:
        """获取所有产品"""
        products = db.get_all_products()
        return [product.to_dict() for product in products]

    def get_products_by_category(self, category: str) -> List[Dict[str, Any]]:
        """根据分类获取产品"""
        products = db.get_products_by_category(category)
        return [product.to_dict() for product in products]

    def search_products(self, keyword: str) -> List[Dict[str, Any]]:
        """搜索产品（按名称）"""
        all_products = db.get_all_products()
        keyword_lower = keyword.lower()
        results = [
            p.to_dict() for p in all_products
            if keyword_lower in p.name.lower() or keyword_lower in p.description.lower()
        ]
        return results

    def get_products_by_price_range(
        self, min_price: float, max_price: float
    ) -> List[Dict[str, Any]]:
        """根据价格范围获取产品"""
        all_products = db.get_all_products()
        results = [
            p.to_dict() for p in all_products
            if min_price <= p.price <= max_price
        ]
        return results

    def get_low_stock_products(self, threshold: int = 10) -> List[Dict[str, Any]]:
        """获取低库存产品"""
        all_products = db.get_all_products()
        results = [
            p.to_dict() for p in all_products
            if p.stock <= threshold
        ]
        return results

    def get_category_statistics(self) -> Dict[str, Any]:
        """获取分类统计信息"""
        all_products = db.get_all_products()

        category_stats = {}
        for product in all_products:
            category = product.category
            if category not in category_stats:
                category_stats[category] = {
                    'count': 0,
                    'total_stock': 0,
                    'total_value': 0.0
                }

            category_stats[category]['count'] += 1
            category_stats[category]['total_stock'] += product.stock
            category_stats[category]['total_value'] += product.price * product.stock

        return category_stats


# 全局产品服务实例
product_service = ProductService()
