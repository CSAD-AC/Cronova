#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
MinIO脚本示例 - 统一日志系统使用演示
这是一个完整的从MinIO下载执行的Python脚本示例
展示了如何使用统一日志系统进行日志记录

使用方法:
1. 这个脚本可以上传到MinIO
2. 通过任务调度系统下载并执行
3. 自动使用统一日志输出功能
"""

import sys
import os
import time
import json
import requests
from datetime import datetime

# ==========================================
# 统一日志系统导入（重要！）
# ==========================================
from utils import logger

# ==========================================
# 任务配置
# ==========================================
class TaskConfig:
    """任务配置类"""
    def __init__(self):
        self.task_id = "demo_task_001"
        self.task_name = "MinIO数据处理示例"
        self.timeout = 300  # 5分钟超时
        self.retry_count = 3
        self.batch_size = 100

def load_task_config():
    """加载任务配置"""
    logger.info("正在加载任务配置...")
    try:
        config = TaskConfig()
        logger.info("任务配置加载成功:")
        logger.info("  任务ID: %s", config.task_id)
        logger.info("  任务名称: %s", config.task_name)
        logger.info("  超时时间: %d 秒", config.timeout)
        logger.info("  批处理大小: %d", config.batch_size)
        return config
    except Exception as e:
        logger.error("加载任务配置失败: %s", str(e))
        raise

# ==========================================
# 数据处理模块
# ==========================================
class DataProcessor:
    """数据处理器"""
    
    def __init__(self, config):
        self.config = config
        self.processed_count = 0
        self.error_count = 0
        self.start_time = None
        
    def start_processing(self):
        """开始数据处理"""
        self.start_time = time.time()
        logger.info("=" * 60)
        logger.info("开始执行数据处理任务")
        logger.info("=" * 60)
        logger.info("任务ID: %s", self.config.task_id)
        logger.info("开始时间: %s", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        
    def simulate_data_loading(self):
        """模拟从MinIO加载数据"""
        logger.info("步骤1: 从MinIO加载数据...")
        
        try:
            # 模拟数据加载过程
            logger.info("正在连接到MinIO...")
            time.sleep(0.5)
            logger.debug("连接MinIO成功")
            
            logger.info("正在获取数据列表...")
            time.sleep(0.3)
            logger.debug("获取数据列表成功")
            
            # 模拟数据
            mock_data = [
                {"id": 1, "name": "产品A", "price": 99.99, "category": "电子产品"},
                {"id": 2, "name": "产品B", "price": 199.99, "category": "家居用品"},
                {"id": 3, "name": "产品C", "price": 299.99, "category": "服装"},
                {"id": 4, "name": "产品D", "price": 399.99, "category": "电子产品"},
                {"id": 5, "name": "产品E", "price": 499.99, "category": "运动用品"},
            ]
            
            logger.info("数据加载完成，共 %d 条记录", len(mock_data))
            logger.debug("数据预览: %s", json.dumps(mock_data[:2], ensure_ascii=False))
            
            return mock_data
            
        except Exception as e:
            logger.exception("从MinIO加载数据失败: %s", str(e))
            raise
    
    def process_data_batch(self, data_batch):
        """处理数据批次"""
        logger.info("步骤2: 处理数据批次 (共 %d 条)", len(data_batch))
        
        processed_items = []
        errors = []
        
        for i, item in enumerate(data_batch, 1):
            try:
                logger.debug("正在处理第 %d 条数据: %s", i, item["name"])
                
                # 数据验证
                if not self._validate_item(item):
                    logger.warning("数据验证失败，跳过: %s", item["name"])
                    errors.append(f"数据验证失败: {item['name']}")
                    continue
                
                # 数据处理逻辑
                processed_item = self._process_single_item(item)
                processed_items.append(processed_item)
                
                # 模拟处理延迟
                time.sleep(0.1)
                
                logger.debug("第 %d 条数据处理完成", i)
                
            except Exception as e:
                logger.error("处理第 %d 条数据时发生错误: %s", i, str(e))
                errors.append(f"处理失败: {item['name']} - {str(e)}")
                self.error_count += 1
        
        logger.info("批次处理完成. 成功: %d, 失败: %d", 
                    len(processed_items), len(errors))
        
        return processed_items, errors
    
    def _validate_item(self, item):
        """验证单个数据项"""
        # 模拟验证逻辑
        required_fields = ["id", "name", "price"]
        for field in required_fields:
            if field not in item or not item[field]:
                logger.warning("缺少必需字段: %s", field)
                return False
        
        if item["price"] <= 0:
            logger.warning("价格无效: %s", item["price"])
            return False
        
        return True
    
    def _process_single_item(self, item):
        """处理单个数据项"""
        # 模拟数据处理
        processed = {
            "original_id": item["id"],
            "name": item["name"],
            "original_price": item["price"],
            "processed_price": round(item["price"] * 1.1, 2),  # 涨价10%
            "category": item["category"],
            "processed_time": datetime.now().isoformat(),
            "status": "processed"
        }
        
        return processed
    
    def save_processed_data(self, processed_items):
        """保存处理后的数据"""
        logger.info("步骤3: 保存处理后的数据...")
        
        try:
            logger.info("正在上传处理结果到MinIO...")
            time.sleep(0.5)
            
            # 模拟上传过程
            result_summary = {
                "task_id": self.config.task_id,
                "processed_count": len(processed_items),
                "processed_time": datetime.now().isoformat(),
                "items": processed_items
            }
            
            # 模拟保存到文件
            output_filename = f"processed_{self.config.task_id}_{int(time.time())}.json"
            logger.info("处理结果已保存到: %s", output_filename)
            logger.debug("结果摘要: %s", json.dumps(result_summary["items"][:1], ensure_ascii=False, indent=2))
            
            logger.info("数据保存完成")
            
        except Exception as e:
            logger.exception("保存处理结果失败: %s", str(e))
            raise
    
    def generate_summary_report(self, processed_items, errors):
        """生成汇总报告"""
        logger.info("步骤4: 生成汇总报告...")
        
        try:
            # 计算统计信息
            total_processed = len(processed_items)
            total_errors = len(errors)
            total_time = time.time() - self.start_time if self.start_time else 0
            
            # 按类别统计
            category_stats = {}
            for item in processed_items:
                category = item["category"]
                if category not in category_stats:
                    category_stats[category] = 0
                category_stats[category] += 1
            
            # 生成报告
            report = {
                "task_id": self.config.task_id,
                "task_name": self.config.task_name,
                "execution_time": {
                    "start": datetime.fromtimestamp(self.start_time).strftime("%Y-%m-%d %H:%M:%S") if self.start_time else "unknown",
                    "end": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                    "duration_seconds": round(total_time, 2)
                },
                "statistics": {
                    "total_processed": total_processed,
                    "total_errors": total_errors,
                    "success_rate": f"{(total_processed / (total_processed + total_errors) * 100):.1f}%" if (total_processed + total_errors) > 0 else "0%",
                    "avg_processing_time": f"{round(total_time / max(total_processed, 1), 3)}秒/条" if total_processed > 0 else "0秒"
                },
                "category_breakdown": category_stats,
                "errors": errors[:10]  # 只显示前10个错误
            }
            
            # 输出报告
            logger.info("=" * 60)
            logger.info("任务执行汇总报告")
            logger.info("=" * 60)
            logger.info("任务ID: %s", report["task_id"])
            logger.info("任务名称: %s", report["task_name"])
            logger.info("执行时间: %s", report["execution_time"]["start"])
            logger.info("结束时间: %s", report["execution_time"]["end"])
            logger.info("总耗时: %s 秒", report["execution_time"]["duration_seconds"])
            logger.info("")
            logger.info("处理统计:")
            logger.info("  总处理数: %d", report["statistics"]["total_processed"])
            logger.info("  总错误数: %d", report["statistics"]["total_errors"])
            logger.info("  成功率: %s", report["statistics"]["success_rate"])
            logger.info("  平均处理时间: %s", report["statistics"]["avg_processing_time"])
            logger.info("")
            logger.info("类别分布:")
            for category, count in category_stats.items():
                logger.info("  %s: %d 条", category, count)
            
            if errors:
                logger.info("")
                logger.info("错误列表 (前10个):")
                for i, error_msg in enumerate(errors[:10], 1):
                    logger.info("  %d. %s", i, error_msg)
            
            logger.info("=" * 60)
            
            return report
            
        except Exception as e:
            logger.exception("生成汇总报告失败: %s", str(e))
            raise
    
    def finish_processing(self):
        """完成处理"""
        end_time = time.time()
        total_time = end_time - self.start_time if self.start_time else 0
        
        logger.info("=" * 60)
        logger.info("数据处理任务完成")
        logger.info("总耗时: %.2f 秒", total_time)
        logger.info("=" * 60)
        
        logger.info("任务状态: COMPLETED")
        logger.info("任务ID: %s", self.config.task_id)
        logger.info("处理结果: SUCCESS")

# ==========================================
# 主函数
# ==========================================
def main():
    """主函数 - 完整的数据处理流程"""
    
    try:
        # 使用logger对象进行日志记录
        logger.info("开始执行MinIO数据处理示例脚本")
        logger.info("脚本启动时间: %s", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        
        # 步骤1: 加载配置
        config = load_task_config()
        
        # 步骤2: 创建处理器
        processor = DataProcessor(config)
        processor.start_processing()
        
        # 步骤3: 加载数据
        data = processor.simulate_data_loading()
        
        # 步骤4: 分批处理数据
        batch_size = config.batch_size
        all_processed = []
        all_errors = []
        
        for i in range(0, len(data), batch_size):
            batch = data[i:i + batch_size]
            logger.info("处理第 %d 批数据 (共 %d 条)", (i // batch_size) + 1, len(batch))
            
            processed_batch, errors_batch = processor.process_data_batch(batch)
            all_processed.extend(processed_batch)
            all_errors.extend(errors_batch)
        
        # 步骤5: 保存结果
        processor.save_processed_data(all_processed)
        
        # 步骤6: 生成报告
        report = processor.generate_summary_report(all_processed, all_errors)
        
        # 步骤7: 完成处理
        processor.finish_processing()
        
        # 成功结束
        logger.info("脚本执行成功完成")
        logger.info("最终状态: SUCCESS")
        
        return True
        
    except KeyboardInterrupt:
        logger.warning("用户中断执行")
        logger.warning("任务被用户中断")
        return False
        
    except Exception as e:
        logger.error("=" * 60)
        logger.error("脚本执行失败!")
        logger.error("=" * 60)
        logger.error("失败原因: %s", str(e))
        logger.exception("详细错误信息:", exc_info=True)
        
        logger.error("任务状态: FAILED")
        logger.error("任务ID: %s", "demo_task_001")
        logger.error("错误信息: %s", str(e))
        
        return False

# ==========================================
# 程序入口
# ==========================================
if __name__ == "__main__":
    try:
        # 使用logger对象进行日志记录
        logger.info("程序启动: %s", __file__)
        
        # 执行主逻辑
        success = main()
        
        # 设置退出码
        if success:
            logger.info("程序正常结束，退出码: 0")
            exit_code = 0
        else:
            logger.error("程序异常结束，退出码: 1")
            exit_code = 1
            
    except Exception as e:
        # 最后的异常处理
        logger.critical("程序发生未捕获的异常: %s", str(e))
        logger.critical("请检查日志获取详细信息")
        exit_code = 1
    
    finally:
        logger.info("程序结束时间: %s", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        logger.info("感谢使用统一日志系统!")
    
    # 退出程序
    sys.exit(exit_code)