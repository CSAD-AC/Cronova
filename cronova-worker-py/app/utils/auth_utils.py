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
        params: dict = None,  # 查询参数
        method: str = "POST",
        url_path: str = ""
    ) -> str:
        """生成签名（算法须与服务端保持一致）"""
        if not all([Config.ACCESS_KEY, Config.SECRET_KEY]):
            raise ValueError("ACCESS_KEY或SECRET_KEY未初始化")

        # 生成请求体字符串
        if json_data:
            body_str = json.dumps(json_data, sort_keys=True, ensure_ascii=False)
        else:
            body_str = "{}"

        # 待签名字符串仅用timestamp（不含请求体）
        sign_str = timestamp
        signature = hmac.new(
            Config.SECRET_KEY.encode("utf-8"),
            sign_str.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()
        return signature
