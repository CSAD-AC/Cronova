import logging
from app.utils.logger import logger as unified_logger


def setup_logger():
    """设置日志配置 - 保持向后兼容
    
    注意：现在只使用控制台输出，不再写入文件
    """
    # 为特定模块设置日志级别
    # 减少心跳和状态更新的日志输出
    logging.getLogger('app.service.report_service').setLevel(logging.WARNING)
    # 减少apscheduler的日志输出
    logging.getLogger('apscheduler').setLevel(logging.WARNING)
    
    return unified_logger


def get_logger(name=None):
    """
    获取日志器 - 保持向后兼容
    
    Args:
        name (str, optional): 日志器名称. 如果为None，返回统一日志器
        
    Returns:
        logging.Logger: 日志器对象
    """
    return unified_logger


# 导出统一日志方法到模块级别，方便直接导入使用
debug = unified_logger.debug
info = unified_logger.info
warning = unified_logger.warning
error = unified_logger.error
critical = unified_logger.critical
exception = unified_logger.exception

# 全局日志器初始化
setup_logger()