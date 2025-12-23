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

        # 解析响应
        try:
            validate_resp = AKSKValidateResponse(**response)
            return validate_resp.code == 200 and validate_resp.data.get("valid", False)
        except Exception as e:
            print(f"解析AKSK验证响应失败：{str(e)}")
            return False
