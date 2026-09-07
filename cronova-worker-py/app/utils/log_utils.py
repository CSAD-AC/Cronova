import logging
from app.utils.logger import logger as unified_logger


def setup_logger():
    """设置日志配置（仅控制台输出）"""
    # 降低心跳/状态模块与 apscheduler 的日志级别，减少噪音
    logging.getLogger('app.service.report_service').setLevel(logging.WARNING)
    logging.getLogger('apscheduler').setLevel(logging.WARNING)
    
    return unified_logger


def get_logger(name=None):
    """获取统一日志器（name 参数保留以兼容调用方）"""
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