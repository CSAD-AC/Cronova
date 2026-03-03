import logging
import sys
import threading
import os

# ANSI 颜色码（用于终端高亮）
class Color:
    RESET = "\033[0m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    BLUE = "\033[34m"
    MAGENTA = "\033[35m"
    CYAN = "\033[36m"
    WHITE = "\033[37m"
    BOLD = "\033[1m"

# 判断是否在支持颜色的终端中（非 Windows 或使用现代终端）
def supports_color():
    return (
        hasattr(sys.stderr, "isatty") and sys.stderr.isatty()
        and not (sys.platform == "win32" and not ("WT_SESSION" in os.environ or "TERM" in os.environ))
    )

# 自定义带颜色的 Formatter
class ColoredFormatter(logging.Formatter):
    LEVEL_COLORS = {
        "DEBUG": Color.CYAN,
        "INFO": Color.GREEN,
        "WARNING": Color.YELLOW,
        "ERROR": Color.RED,
        "CRITICAL": Color.MAGENTA + Color.BOLD,
    }

    def __init__(self, use_color=True):
        super().__init__()
        self.use_color = use_color

    def format(self, record):
        # 先格式化消息（支持 %s, %d 等格式化）
        try:
            if record.args:
                message = record.msg % record.args
            else:
                message = record.msg
        except (TypeError, ValueError):
            # 如果格式化失败，直接使用原始消息
            message = record.getMessage()

        # 基础格式
        log_time = self.formatTime(record, "%Y-%m-%d %H:%M:%S")
        level_name = record.levelname

        # 获取调用位置（模块名、函数名、行号）
        filename = os.path.basename(record.pathname)
        lineno = record.lineno
        func_name = record.funcName

        # 构建基础字符串
        log_str = f"{log_time} | {level_name:<8} | {filename}:{func_name}:{lineno} - {message}"

        # 如果有异常信息，追加 traceback
        if record.exc_info:
            log_str += "\n" + self.formatException(record.exc_info)

        # 添加颜色（如果支持）
        if self.use_color:
            color = self.LEVEL_COLORS.get(level_name, "")
            log_str = f"{color}{log_str}{Color.RESET}" if color else log_str

        return log_str


# 单例日志管理器（避免重复初始化）
class UnifiedLogger:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls, level=logging.INFO):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
                    cls._instance._initialized = False
        return cls._instance

    def __init__(self, level=logging.INFO):
        if self._initialized:
            return
        self._initialized = True

        self.logger = logging.getLogger("UnifiedLogger")
        self.logger.setLevel(logging.DEBUG)  # 设为最低，由 handler 控制实际级别

        # 避免重复添加 handler
        if self.logger.handlers:
            return

        # 只创建控制台 handler（移除文件写入功能）
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(level)

        # 设置带颜色的 formatter（自动检测）
        use_color = supports_color()
        formatter = ColoredFormatter(use_color=use_color)
        console_handler.setFormatter(formatter)

        self.logger.addHandler(console_handler)

    def debug(self, msg, *args, **kwargs):
        self.logger.debug(msg, *args, **kwargs)

    def info(self, msg, *args, **kwargs):
        self.logger.info(msg, *args, **kwargs)

    def warning(self, msg, *args, **kwargs):
        self.logger.warning(msg, *args, **kwargs)

    def error(self, msg, *args, **kwargs):
        self.logger.error(msg, *args, **kwargs)

    def critical(self, msg, *args, **kwargs):
        self.logger.critical(msg, *args, **kwargs)

    def exception(self, msg, *args, **kwargs):
        kwargs["exc_info"] = True
        self.logger.error(msg, *args, **kwargs)


# 全局便捷实例（推荐使用方式）
_default_logger = UnifiedLogger(level=logging.INFO).logger
_debug_logger = UnifiedLogger(level=logging.DEBUG).logger
# 导出常用方法，使用体验像 loguru
debug = _default_logger.debug
info = _default_logger.info
warning = _default_logger.warning
error = _default_logger.error
critical = _default_logger.critical
exception = _default_logger.exception

# 也可直接导出 logger 对象
logger = _default_logger
logger_debug = _debug_logger