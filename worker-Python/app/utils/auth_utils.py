import time
import hmac
import hashlib
import json
from app.config import Config


class AuthUtils:
    @staticmethod
    def get_timestamp() -> str:
        """生成当前时间戳（秒级）"""
        return str(int(time.time()))

    @staticmethod
    def generate_signature(
        timestamp: str,
        json_data: dict = None,
        params: dict = None,  # 新增：接收查询参数
        method: str = "POST",
        url_path: str = ""
    ) -> str:
        """
        生成签名（与服务端逻辑统一）
        :param params: 查询参数（GET/POST的query参数）
        :param method: 请求方法（如POST/GET，大写）
        :param url_path: URL路径（如/api/worker/heartbeat/xxx）
        """
        if not all([Config.ACCESS_KEY, Config.SECRET_KEY]):
            raise ValueError("ACCESS_KEY或SECRET_KEY未初始化")

        # 生成请求体字符串
        if json_data:
            body_str = json.dumps(json_data, sort_keys=True, ensure_ascii=False)
        else:
            body_str = "{}"

        # 生成待签名字符串：仅使用timestamp，不再包含requestBody
        sign_str = timestamp
        # 生成签名
        signature = hmac.new(
            Config.SECRET_KEY.encode("utf-8"),
            sign_str.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()
        return signature
