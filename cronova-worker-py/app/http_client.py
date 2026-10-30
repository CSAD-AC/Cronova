import json
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
            is_register = url == Config.WORKER_REGISTER_URL
            is_validate = url == Config.AKSK_VALIDATE_URL

            # 注册请求携带注册令牌（防止 SK 白嫖；无令牌配置则不携带）
            if is_register and Config.REGISTER_TOKEN:
                headers["X-Register-Token"] = Config.REGISTER_TOKEN

            # 除注册/验证外的 POST 请求添加签名头；body 参与签名且以原文发送，保证验签一致
            if not is_register and not is_validate:
                timestamp = AuthUtils.get_timestamp()
                from urllib.parse import urlparse
                parsed_url = urlparse(url)
                url_path = parsed_url.path  # 例如：/api/worker/heartbeat/worker_xxx
                body_str = json.dumps(json_data, sort_keys=True, ensure_ascii=False) if json_data else None
                signature = AuthUtils.generate_signature(
                    timestamp=timestamp,
                    body=body_str,
                    params=params,
                    method="POST",
                    url_path=url_path
                )
                headers.update({
                    "X-Access-Key": Config.ACCESS_KEY,
                    "X-Timestamp": timestamp,
                    "X-Signature": signature
                })
                if body_str is not None:
                    # 有 body：以签名用的原文发送，保证服务端收到的 body 与签名一致
                    headers["Content-Type"] = "application/json"
                    response = requests.post(url, data=body_str.encode("utf-8"), params=params,
                                             headers=headers, timeout=5)
                else:
                    response = requests.post(url, json=json_data, params=params, headers=headers, timeout=5)
            else:
                response = requests.post(url, json=json_data, params=params, headers=headers, timeout=5)

            response.raise_for_status()
            return response.json()
        except Exception as e:
            print(f"POST请求失败（{url}）：{str(e)}")
            return None
