from app.config import Config
from app.http_client import HttpClient
from app.model.worker import AKSKValidateResponse


class AuthService:
    @staticmethod
    def validate_aksk() -> bool:
        """验证AKSK有效性（使用本地配置的ACCESS_KEY）"""
        if not Config.ACCESS_KEY:
            print("ACCESS_KEY未初始化，无法验证")
            return False

        # 构造请求参数（Query参数：accessKey）
        params = {"accessKey": Config.ACCESS_KEY}
        response = HttpClient.post(Config.AKSK_VALIDATE_URL, params=params)
        if not response:
            return False

        try:
            validate_resp = AKSKValidateResponse(**response)
            # 处理data字段为None的情况
            is_valid = validate_resp.data.get("valid", False) if validate_resp.data else False
            return validate_resp.code == 200 and is_valid
        except Exception as e:
            print(f"解析AKSK验证响应失败：{str(e)}")
            return False
