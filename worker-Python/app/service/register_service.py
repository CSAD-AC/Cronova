from app.config import Config
from app.http_client import HttpClient
from app.model.worker import WorkerRegisterRequest
from app.utils.ip_utils import IPUtils
from app.service.tag_report_service import TagReportService


class RegisterService:
    @staticmethod
    def register_worker() -> bool:
        """注册Worker，获取WorkerID、AKSK并持久化"""
        # 1. 初始化本机Host和IP
        Config.HOST = IPUtils.get_local_host()
        Config.IP = IPUtils.get_local_ip()

        # 2. 构造注册请求
        register_req = WorkerRegisterRequest(
            host=Config.HOST,
            ip=Config.IP,
            tags=Config.WORKER_TAGS
        )
        print(f"[Register] 注册请求: host={Config.HOST}, ip={Config.IP}, tags={Config.WORKER_TAGS}")
        # 3. 发送注册请求
        response = HttpClient.post(Config.WORKER_REGISTER_URL, json_data=register_req.dict())
        if not response:
            print("注册请求发送失败")
            return False

        # 4. 解析响应
        try:
            Config.WORKER_ID = response.get("data", {}).get("workerId")
            Config.ACCESS_KEY = response.get("data", {}).get("accessKey")
            Config.SECRET_KEY = response.get("data", {}).get("secretKey")

            # 校验必要字段
            if not all([Config.WORKER_ID, Config.ACCESS_KEY, Config.SECRET_KEY]):
                print("调度器响应中缺少WorkerID/ACCESS_KEY/SECRET_KEY")
                return False

            # 5. 替换模板中的占位符，生成实际接口地址
            Config.WORKER_HEARTBEAT_URL = Config.WORKER_HEARTBEAT_URL_TEMPLATE.format(worker_id=Config.WORKER_ID)
            Config.WORKER_STATUS_REPORT_URL = Config.WORKER_STATUS_REPORT_URL_TEMPLATE.format(worker_id=Config.WORKER_ID)

            # 6. 保存到本地配置文件
            Config.save_local_config()

            # 7. 初始化标签上报服务
            TagReportService.init_reported_tags(Config.WORKER_TAGS)

            print(f"Worker注册成功，ID：{Config.WORKER_ID}，AK：{Config.ACCESS_KEY}")
            return True

        except Exception as e:
            print(f"解析注册响应失败：{str(e)}")
            return False
