import requests
from typing import Dict, Optional
from app.config import Config
from app.utils.auth_utils import AuthUtils


class HttpClient:
    @staticmethod
    def get(url: str, params: Optional[Dict] = None) -> Optional[Dict]:
        try:
            response = requests.get(url, params=params, timeout=5)
            response.raise_for_status()
            return response.json()
        except Exception as e:
            print(f"GET请求失败（{url}）：{str(e)}")
            return None

    @staticmethod
    def post(
            url: str,
            json_data: Optional[Dict] = None,
            params: Optional[Dict] = None
    ) -> Optional[Dict]:
        try:
            headers = {}
            # 排除注册请求，其他POST请求添加认证头
            if url != Config.WORKER_REGISTER_URL:
                # 生成时间戳
                timestamp = AuthUtils.get_timestamp()
                # 生成签名
                from urllib.parse import urlparse
                parsed_url = urlparse(url)
                url_path = parsed_url.path  # 例如：/api/worker/heartbeat/worker_xxx
                # 传入请求方法（POST）和路径生成签名
                signature = AuthUtils.generate_signature(
                    timestamp=timestamp,
                    json_data=json_data,
                    params=params,  # 新增：传入查询参数
                    method="POST",
                    url_path=url_path
                )
                # 添加认证头
                headers = {
                    "X-Access-Key": Config.ACCESS_KEY,
                    "X-Timestamp": timestamp,
                    "X-Signature": signature
                }

            response = requests.post(
                url,
                json=json_data,
                params=params,
                headers=headers,
                timeout=5
            )
            response.raise_for_status()
            return response.json()
        except Exception as e:
            print(f"POST请求失败（{url}）：{str(e)}")
            return None
