import json
import os
from typing import List
from app.utils.env_utils import EnvUtils


class Config:
    # 认证配置
    LOCAL_CONFIG_PATH = os.getenv("LOCAL_CONFIG_PATH", os.path.join(os.path.dirname(os.path.abspath(__file__)), "local_config.json"))

    # 调度器接口地址（真实地址通过环境变量 SCHEDULER_BASE_URL 或 .env 注入）
    SCHEDULER_BASE_URL = os.getenv("SCHEDULER_BASE_URL", "http://127.0.0.1:8085")
    AKSK_VALIDATE_URL = f"{SCHEDULER_BASE_URL}/api/worker/validate-aksk"  # AKSK验证接口
    WORKER_REGISTER_URL = f"{SCHEDULER_BASE_URL}/api/worker/register"  # Worker注册接口
    WORKER_HEARTBEAT_URL_TEMPLATE = f"{SCHEDULER_BASE_URL}/api/worker/heartbeat/{{worker_id}}"  # 心跳接口
    WORKER_STATUS_REPORT_URL_TEMPLATE = f"{SCHEDULER_BASE_URL}/api/worker/status/{{worker_id}}"  # 状态上报接口

    # 定时配置
    HEARTBEAT_INTERVAL = int(os.getenv("HEARTBEAT_INTERVAL", "2"))  # 心跳间隔（秒）
    STATUS_REPORT_INTERVAL = int(os.getenv("STATUS_REPORT_INTERVAL", "10"))  # 状态上报间隔（秒）

    # RocketMQ配置
    MQ_NAMESRV_ADDR = os.getenv("MQ_NAMESRV_ADDR", "127.0.0.1:9876")
    MQ_CONSUMER_GROUP = os.getenv("MQ_CONSUMER_GROUP", "WORKER_EXECUTOR_GROUP")
    MQ_TASK_TOPIC = os.getenv("MQ_TASK_TOPIC", "TASK_EXECUTE")
    MQ_PRODUCER_GROUP = os.getenv("MQ_PRODUCER_GROUP", "WORKER_EXECUTOR_GROUP")
    MQ_TASK_RESULT_TOPIC = os.getenv("MQ_TASK_RESULT_TOPIC", "TASK_RESULT")
    MQ_TASK_CONTROL_TOPIC = os.getenv("MQ_TASK_CONTROL_TOPIC", "TASK_CONTROL")

    # MinIO配置
    MINIO_ENDPOINT = os.getenv("MINIO_ENDPOINT", "http://127.0.0.1:9000")
    MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "changeme")
    MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "changeme")
    MINIO_SECURE = os.getenv("MINIO_SECURE", "False").lower() == "true"
    MINIO_BUCKET_NAME = os.getenv("MINIO_BUCKET_NAME", "task-engine")  # 统一使用 task-engine bucket

    # Worker动态信息
    WORKER_ID = None  # 注册后由调度器返回
    ACCESS_KEY = None  # 注册后由调度器返回
    SECRET_KEY = None  # 注册后由调度器返回
    HOST = None  # 本机主机名
    IP = None  # 本机IP

    # Worker标签配置（支持多种来源）
    # 1. 环境变量 WORKER_TAGS（最高优先级）
    # 2. .env 文件 WORKER_TAGS
    # 3. 配置文件 local_config.json
    # 4. 默认值（空列表）
    # 格式: ["python:3.9", "java:11"]
    WORKER_TAGS: List[str] = []

    # Worker标签解析（只需要 name，用于任务依赖匹配）
    # 格式: ["python", "java"]
    TAGS: List[str] = []

    WORKER_HEARTBEAT_URL = None
    WORKER_STATUS_REPORT_URL = None

    @classmethod
    def _parse_tags(cls, tags: List[str]) -> List[str]:
        """将带版本的标签解析为名称列表"""
        parsed = []
        for tag in tags:
            if ":" in tag:
                name = tag.split(":", 1)[0].strip()
            else:
                name = tag.strip()
            if name:
                parsed.append(name.lower())
        return parsed

    @classmethod
    def load_worker_tags(cls) -> List[str]:
        """加载 Worker 标签，支持多种来源（优先级：环境变量 > .env文件 > 配置文件 > 默认值）"""
        tags = []

        # 1. 优先从环境变量读取
        env_tags = os.getenv("WORKER_TAGS")
        if env_tags:
            tags = [t.strip() for t in env_tags.split(",") if t.strip()]
            if tags:
                print(f"[Config] 从环境变量加载标签: {tags}")
                cls.TAGS = cls._parse_tags(tags)
                return tags

        # 2. 尝试从 .env 文件读取（如果存在）
        # .env 文件可能位于 app 目录或 docker 目录
        possible_env_paths = [
            os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docker", ".env"),
            os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".env")
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
                                        print(f"[Config] 从 .env 文件加载标签: {tags}")
                                        cls.TAGS = cls._parse_tags(tags)
                                        return tags
                except Exception as e:
                    print(f"[Config] 读取 .env 文件标签失败: {e}")

        # 3. 尝试从 local_config.json 读取
        if os.path.exists(cls.LOCAL_CONFIG_PATH):
            try:
                with open(cls.LOCAL_CONFIG_PATH, "r", encoding="utf-8") as f:
                    config = json.load(f)
                    worker_tags = config.get("worker_tags", [])
                    if worker_tags:
                        print(f"[Config] 从配置文件加载标签: {worker_tags}")
                        cls.TAGS = cls._parse_tags(worker_tags)
                        return worker_tags
            except Exception as e:
                print(f"[Config] 读取配置文件标签失败: {e}")

        # 4. 默认空列表
        print("[Config] 未配置 Worker 标签，将使用空标签（不限制任务匹配）")
        cls.TAGS = []
        return []

    @classmethod
    def load_local_config(cls):
        """从本地文件加载AKSK、WorkerID等配置"""
        if not os.path.exists(cls.LOCAL_CONFIG_PATH):
            print("本地配置文件不存在，将执行注册流程")
            return False
        try:
            with open(cls.LOCAL_CONFIG_PATH, "r", encoding="utf-8") as f:
                local_config = json.load(f)
            cls.WORKER_ID = local_config.get("worker_id")
            cls.ACCESS_KEY = local_config.get("access_key")
            cls.SECRET_KEY = local_config.get("secret_key")
            if cls.WORKER_ID:
                cls.WORKER_HEARTBEAT_URL = cls.WORKER_HEARTBEAT_URL_TEMPLATE.format(worker_id=cls.WORKER_ID)
                cls.WORKER_STATUS_REPORT_URL = cls.WORKER_STATUS_REPORT_URL_TEMPLATE.format(worker_id=cls.WORKER_ID)
            print("本地配置加载成功")
            return True
        except Exception as e:
            print(f"加载本地配置失败：{str(e)}")
            return False

    @classmethod
    def save_local_config(cls):
        """将注册返回的AKSK、WorkerID等保存到本地文件"""
        local_config = {
            "worker_id": cls.WORKER_ID,
            "access_key": cls.ACCESS_KEY,
            "secret_key": cls.SECRET_KEY,
            "worker_tags": cls.WORKER_TAGS
        }
        try:
            with open(cls.LOCAL_CONFIG_PATH, "w", encoding="utf-8") as f:
                json.dump(local_config, f, indent=4)
            print("本地配置保存成功")
            return True
        except Exception as e:
            print(f"保存本地配置失败：{str(e)}")
            return False


# 初始化时加载标签
Config.WORKER_TAGS = Config.load_worker_tags()
