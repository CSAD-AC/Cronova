import logging
import os
from logging.handlers import RotatingFileHandler


def setup_logger():
    """设置日志配置"""
    # 创建日志文件夹
    log_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), 'logs')
    os.makedirs(log_dir, exist_ok=True)
    
    # 配置日志格式
    formatter = logging.Formatter(
        '%(asctime)s - %(name)s - %(levelname)s - %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    # 创建文件处理器（按大小切割，保留5个备份）
    file_handler = RotatingFileHandler(
        os.path.join(log_dir, 'worker.log'),
        maxBytes=10*1024*1024,  # 10MB
        backupCount=5
    )
    file_handler.setFormatter(formatter)
    file_handler.setLevel(logging.DEBUG)
    
    # 创建控制台处理器
    console_handler = logging.StreamHandler()
    console_handler.setFormatter(formatter)
    console_handler.setLevel(logging.INFO)
    
    # 获取根日志器
    root_logger = logging.getLogger()
    root_logger.setLevel(logging.DEBUG)
    root_logger.addHandler(file_handler)
    root_logger.addHandler(console_handler)
    
    # 为特定模块设置日志级别
    # 减少心跳和状态更新的日志输出
    logging.getLogger('app.service.report_service').setLevel(logging.WARNING)
    # 减少apscheduler的日志输出
    logging.getLogger('apscheduler').setLevel(logging.WARNING)
    
    return root_logger


# 初始化日志
def get_logger(name=None):
    """获取日志器"""
    return logging.getLogger(name)


# 全局日志器初始化
setup_logger()