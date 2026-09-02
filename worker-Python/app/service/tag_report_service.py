import os
import json
import time
from typing import List, Set
from app.config import Config
from app.http_client import HttpClient
from app.utils.log_utils import get_logger

logger = get_logger(__name__)


class TagReportService:
    """标签上报服务 - 支持动态检测和更新Worker标签"""

    # 存储上一次上报的标签，用于检测变化
    _last_reported_tags: Set[str] = set()

    @classmethod
    def check_and_report_tags(cls) -> bool:
        """检测标签变化，有变化时上报给调度器"""
        if not Config.WORKER_ID:
            logger.debug("Worker未注册，跳过标签检测")
            return False

        # 重新加载标签配置
        current_tags = cls._reload_tags()

        # 转换为可比较的格式
        current_tags_set = set(current_tags)

        # 检测标签是否有变化
        if current_tags_set == cls._last_reported_tags:
            logger.debug(f"标签未变化，跳过上报，当前标签: {current_tags}")
            return True

        logger.info(f"检测到标签变化，旧的: {cls._last_reported_tags}, 新的: {current_tags_set}")

        # 上报新标签
        success = cls._report_tags_to_scheduler(current_tags)

        if success:
            cls._last_reported_tags = current_tags_set
            # 更新本地配置
            cls._save_tags_to_local_config(current_tags)
            logger.info(f"标签上报成功，新标签: {current_tags}")
        else:
            logger.error(f"标签上报失败，当前标签: {current_tags}")

        return success

    @classmethod
    def _reload_tags(cls) -> List[str]:
        """重新加载标签配置"""
        # 优先从环境变量读取
        env_tags = os.getenv("WORKER_TAGS")
        if env_tags:
            tags = [t.strip() for t in env_tags.split(",") if t.strip()]
            if tags:
                logger.debug(f"从环境变量重新加载标签: {tags}")
                Config.WORKER_TAGS = tags
                Config.TAGS = Config._parse_tags(tags)
                return tags

        # 尝试从 .env 文件读取
        possible_env_paths = [
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docker", ".env"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
        ]

        for env_path in possible_env_paths:
            env_path = os.path.normpath(env_path)
            if os.path.exists(env_path):
                try:
                    with open(env_path, "r", encoding="utf-8") as f:
                        for line in f:
                            line = line.strip()
                            if line.startswith("WORKER_TAGS=") and not line.startswith("#"):
                                env_file_tags = line.split("=", 1)[1].strip()
                                if env_file_tags:
                                    tags = [t.strip() for t in env_file_tags.split(",") if t.strip()]
                                    if tags:
                                        logger.debug(f"从 .env 文件重新加载标签: {tags}")
                                        Config.WORKER_TAGS = tags
                                        Config.TAGS = Config._parse_tags(tags)
                                        return tags
                except Exception as e:
                    logger.warning(f"读取 .env 文件标签失败: {e}")

        # 尝试从 local_config.json 读取
        if os.path.exists(Config.LOCAL_CONFIG_PATH):
            try:
                with open(Config.LOCAL_CONFIG_PATH, "r", encoding="utf-8") as f:
                    config = json.load(f)
                    worker_tags = config.get("worker_tags", [])
                    if worker_tags:
                        logger.debug(f"从配置文件重新加载标签: {worker_tags}")
                        Config.WORKER_TAGS = worker_tags
                        Config.TAGS = Config._parse_tags(worker_tags)
                        return worker_tags
            except Exception as e:
                logger.warning(f"读取配置文件标签失败: {e}")

        # 返回当前配置中的标签
        return Config.WORKER_TAGS if Config.WORKER_TAGS else []

    @classmethod
    def _report_tags_to_scheduler(cls, tags: List[str]) -> bool:
        """上报标签到调度器"""
        if not Config.WORKER_ID:
            logger.warning("Worker未注册，无法上报标签")
            return False

        update_url = f"{Config.SCHEDULER_BASE_URL}/api/worker/tags/{Config.WORKER_ID}"

        try:
            response = HttpClient.post(update_url, json_data={"tags": tags})
            if response and response.get("code") == 200:
                logger.info(f"标签上报调度器成功，tags: {tags}")
                return True
            else:
                logger.error(f"标签上报调度器失败，response: {response}")
                return False
        except Exception as e:
            logger.error(f"标签上报调度器异常: {str(e)}")
            return False

    @classmethod
    def _save_tags_to_local_config(cls, tags: List[str]) -> bool:
        """保存标签到本地配置文件"""
        try:
            # 读取现有配置
            local_config = {}
            if os.path.exists(Config.LOCAL_CONFIG_PATH):
                with open(Config.LOCAL_CONFIG_PATH, "r", encoding="utf-8") as f:
                    local_config = json.load(f)

            # 更新标签
            local_config["worker_tags"] = tags

            # 写入配置
            with open(Config.LOCAL_CONFIG_PATH, "w", encoding="utf-8") as f:
                json.dump(local_config, f, indent=4)

            logger.debug(f"标签已保存到本地配置: {tags}")
            return True
        except Exception as e:
            logger.error(f"保存标签到本地配置失败: {str(e)}")
            return False

    @classmethod
    def init_reported_tags(cls, tags: List[str]):
        """初始化已上报的标签（在Worker注册成功后调用）"""
        cls._last_reported_tags = set(tags)
        logger.info(f"已初始化上报标签: {tags}")
