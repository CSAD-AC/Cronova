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
        body: str = None,  # 实际发送的请求体字符串（服务端以此原文验签）
        params: dict = None,  # 查询参数（保留字段）
        method: str = "POST",
        url_path: str = ""
    ) -> str:
        """生成签名（算法须与服务端保持一致：HMAC-SHA256(SK, timestamp + "\\n" + body)）"""
        if not all([Config.ACCESS_KEY, Config.SECRET_KEY]):
            raise ValueError("ACCESS_KEY或SECRET_KEY未初始化")

        # 生成待签名字符串：无 body 时仅 timestamp，有 body 时追加 body 原文
        sign_str = timestamp if not body else timestamp + "\n" + body
        signature = hmac.new(
            Config.SECRET_KEY.encode("utf-8"),
            sign_str.encode("utf-8"),
            hashlib.sha256
        ).hexdigest()
        return signature
