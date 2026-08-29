"""
回调服务模板

【接口说明】
1. 回调接口：POST /api/callback/template
2. 健康接口：GET  /api/callback/template/health

【健康检查接口约定】
- 路径格式：<回调接口路径>/health（强制约定）
- 当前：/api/callback/template/health
- 功能：仅验证 X-Secret 和 X-Request-Id 请求头
- 要求：健康接口和回调接口必须使用相同的 BaseUrl
"""

import json
import time
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Any, Dict, Optional


class Result:
    """统一返回对象"""
    def __init__(self):
        self.code: int = 200
        self.message: str = "success"
        self.data: Any = None
        self.cost_millis: int = 0
        self.request_id: Optional[str] = None

    @classmethod
    def ok(cls, data: Any = None) -> "Result":
        result = cls()
        result.code = 200
        result.message = "success"
        result.data = data
        return result

    @classmethod
    def fail(cls, code: int, message: str) -> "Result":
        result = cls()
        result.code = code
        result.message = message
        return result

    def to_dict(self) -> Dict:
        return {
            "code": self.code,
            "message": self.message,
            "data": self.data,
            "cost_millis": self.cost_millis,
            "request_id": self.request_id
        }


class TemplateCallback:
    """回调处理器模板"""

    def secret_key(self) -> str:
        """
        返回密钥
        【注意】此密钥需要与调度器回调配置中的密钥保持一致
        """
        return ""  # TODO: 请配置密钥

    def endpoint(self) -> str:
        """
        返回模块名称
        用于健康检查响应的 endpoint 字段
        """
        return ""  # TODO: 请配置模块名称

    def run_job(self, param: Dict[str, Any]) -> Result:
        """
        执行业务逻辑
        param 中已包含 request_id（由处理器自动注入）
        """
        request_id = param.get("request_id", "")
        print(f"[{request_id}] 开始执行任务")

        # TODO: 在此处编写业务逻辑

        return Result.ok({"message": "任务执行成功"})

    def handle_request(self, method: str, path: str, headers: dict, body: bytes) -> tuple:
        """
        处理 HTTP 请求
        返回: (status_code, response_dict)
        """
        start_time = time.time()
        request_id = headers.get("X-Request-Id", "")

        if not request_id:
            return 400, Result.fail(400, "缺少请求头 X-Request-Id").to_dict()

        try:
            secret = self.secret_key()
            if secret is None or secret == "":
                print(f"[{request_id}] warning: secret_key() 未配置")
        except NotImplementedError:
            return 500, Result.fail(500, "secret_key() 方法未实现").to_dict()

        input_secret = headers.get("X-Secret", "")
        expected_secret = self.secret_key()
        if input_secret != expected_secret:
            print(f"[{request_id}] 密钥验证失败")
            return 401, Result.fail(401, "密钥验证失败").to_dict()

        if method == "GET" and path == "/api/callback/template/health":
            return self._health_check_impl(request_id, start_time)
        elif method == "POST" and path == "/api/callback/template":
            return self._handle_impl(request_id, headers, body, start_time)
        else:
            return 404, Result.fail(404, "接口不存在").to_dict()

    def _handle_impl(self, request_id: str, headers: dict, body: bytes, start_time: float) -> tuple:
        """处理回调请求"""
        try:
            if body:
                try:
                    param = json.loads(body.decode("utf-8"))
                except:
                    param = {}
            else:
                param = {}

            param["request_id"] = request_id
            result = self.run_job(param)

            if result is None:
                result = Result.ok()

            result.cost_millis = int((time.time() - start_time) * 1000)
            result.request_id = request_id

            return 200, result.to_dict()

        except Exception as e:
            print(f"[{request_id}] 任务执行出错: {str(e)}")
            return 500, Result.fail(500, "执行失败").to_dict()

    def _health_check_impl(self, request_id: str, start_time: float) -> tuple:
        """健康检查实现"""
        try:
            health_data = {
                "status": "UP",
                "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime()),
                "endpoint": self.endpoint(),
                "message": "服务正常"
            }

            result = Result.ok(health_data)
            result.cost_millis = int((time.time() - start_time) * 1000)
            result.request_id = request_id

            print(f"[{request_id}] 健康检查通过，endpoint: {self.endpoint()}")
            return 200, result.to_dict()

        except Exception as e:
            print(f"[{request_id}] 健康检查异常: {str(e)}")
            return 500, Result.fail(500, f"健康检查异常: {str(e)}").to_dict()


job = TemplateCallback()


class RequestHandler(BaseHTTPRequestHandler):
    """HTTP 请求处理器"""

    def do_GET(self):
        """处理 GET 请求"""
        headers = {k: v for k, v in self.headers.items()}
        status_code, response = job.handle_request("GET", self.path, headers, b"")
        self._send_response(status_code, response)

    def do_POST(self):
        """处理 POST 请求"""
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else b""
        headers = {k: v for k, v in self.headers.items()}
        status_code, response = job.handle_request("POST", self.path, headers, body)
        self._send_response(status_code, response)

    def _send_response(self, status_code: int, response: dict):
        """发送响应"""
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode("utf-8"))

    def log_message(self, format, *args):
        """禁用默认日志"""
        pass


def run_server(port: int = 8080):
    """启动 HTTP 服务器"""
    server_address = ("", port)
    httpd = HTTPServer(server_address, RequestHandler)
    print("=" * 50)
    print("✅ 回调服务已启动（原生 Python）")
    print("📌 回调接口: POST /api/callback/template")
    print("📌 健康接口: GET  /api/callback/template/health")
    print(f"🌐 监听地址: http://0.0.0.0:{port}")
    print("=" * 50)
    httpd.serve_forever()


if __name__ == "__main__":
    run_server(8080)
