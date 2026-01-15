import json
import os
from app.utils.env_utils import EnvUtils

class Config:
    # 认证配置
    LOCAL_CONFIG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "local_config.json")

    # 调度器接口地址
    SCHEDULER_BASE_URL = "http://127.0.0.1:28085"  # 调度器基础地址
    AKSK_VALIDATE_URL = f"{SCHEDULER_BASE_URL}/api/worker/validate-aksk"  # AKSK验证接口
    WORKER_REGISTER_URL = f"{SCHEDULER_BASE_URL}/api/worker/register"  # Worker注册接口
    WORKER_HEARTBEAT_URL_TEMPLATE = f"{SCHEDULER_BASE_URL}/api/worker/heartbeat/{{worker_id}}"  # 心跳接口
    WORKER_STATUS_REPORT_URL_TEMPLATE = f"{SCHEDULER_BASE_URL}/api/worker/status/{{worker_id}}"  # 状态上报接口

    # 定时配置
    HEARTBEAT_INTERVAL = 2  # 心跳间隔（秒）
    STATUS_REPORT_INTERVAL = 10  # 状态上报间隔（秒）

    # RocketMQ配置
    MQ_NAMESRV_ADDR = "127.0.0.1:9876"
    MQ_CONSUMER_GROUP = "WORKER_EXECUTOR_GROUP"
    MQ_TASK_TOPIC = "TASK_EXECUTE"
    MQ_PRODUCER_GROUP = "WORKER_EXECUTOR_GROUP"
    MQ_TASK_RESULT_TOPIC = "TASK_RESULT"
    MQ_TASK_CONTROL_TOPIC = "TASK_CONTROL"

    # MinIO配置
    MINIO_ENDPOINT = "127.0.0.1:9000"
    MINIO_ACCESS_KEY = "changeme"
    MINIO_SECRET_KEY = "changeme"
    MINIO_SECURE = False
    MINIO_BUCKET_NAME = "task-scripts"  # 与Java调度器配置一致

    # Worker动态信息
    WORKER_ID = None  # 注册后由调度器返回
    ACCESS_KEY = None  # 注册后由调度器返回
    SECRET_KEY = None  # 注册后由调度器返回
    HOST = None  # 本机主机名
    IP = None  # 本机IP
    # 需要检测的目标包列表
    TARGET_PACKAGES = [
        "numpy", "pandas", "minio", "requests", "rocketmq"
    ]
    TAGS = EnvUtils.get_installed_packages(TARGET_PACKAGES)

    WORKER_HEARTBEAT_URL = None
    WORKER_STATUS_REPORT_URL = None

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
            "secret_key": cls.SECRET_KEY
        }
        try:
            with open(cls.LOCAL_CONFIG_PATH, "w", encoding="utf-8") as f:
                json.dump(local_config, f, indent=4)
            print("本地配置保存成功")
            return True
        except Exception as e:
            print(f"保存本地配置失败：{str(e)}")
            return False
