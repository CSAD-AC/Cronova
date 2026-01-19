import time
from abc import ABC, abstractmethod
from typing import Any, Dict, Optional
from flask import request


class Result:
    """统一返回对象"""

    def __init__(self):
        self.code = 200
        self.msg = "success"
        self.data = None
        self.cost_millis = 0
        self.request_id = None

    @classmethod
    def ok(cls, data: Any = None) -> "Result":
        result = cls()
        result.code = 200
        result.msg = "success"
        result.data = data
        return result

    @classmethod
    def fail(cls, code: int, msg: str) -> "Result":
        result = cls()
        result.code = code
        result.msg = msg
        return result

    def to_dict(self) -> Dict:
        return {
            "code": self.code,
            "msg": self.msg,
            "data": self.data,
            "cost_millis": self.cost_millis,
            "request_id": self.request_id
        }


class SecurityException(Exception):
    """安全异常"""
    pass


class ScheduledJobBase(ABC):
    """
    定时任务回调基类

    【使用说明】

    一、核心信息
        - 请求方法：POST
        - 请求头：X-Secret（密钥）、X-Request-Id（请求ID）
        - 请求体：可选 JSON

    二、子类必须实现的方法
        1. secret_key()    - 返回密钥，用于 X-Secret 校验
        2. endpoint()      - 返回模块名称，用于健康检查响应
        3. run_job(param)  - 执行业务逻辑，返回 Result

    三、子类需自行暴露两个接口：
        1. POST 接口接收回调请求，在视图函数中调用 self._handle()
        2. GET 接口用于健康检查，路径为回调接口 + "/health"

    四、接口约定
        【重要】健康检查接口
        - 路径格式：<回调接口路径>/health（强制约定）
        - 示例：
            · 回调接口：/api/callback/data-sync
            · 健康接口：/api/callback/data-sync/health
        - 功能：仅验证 X-Secret 和 X-Request-Id 请求头
        - 要求：健康接口和回调接口必须使用相同的 BaseUrl

    五、日志规范
        - 务必在日志中输出 requestId
        - 示例：print(f"[{request_id}] 开始执行任务")

    【健康检查响应格式】
    {
        "code": 200,
        "msg": "success",
        "data": {
            "status": "UP",
            "timestamp": "2024-01-19T10:30:00",
            "endpoint": "数据同步服务",
            "message": "服务正常"
        },
        "cost_millis": 5,
        "request_id": "abc123def456"
    }

    【示例用法】
        class MyJob(ScheduledJobBase):
            def secret_key(self) -> str:
                return "my-secret"

            def endpoint(self) -> str:
                return "数据同步服务"

            def run_job(self, param: dict) -> Result:
                print(f"[{param.get('request_id')}] 执行业务")
                return Result.ok({"done": True})

        # 在 Flask 中注册回调接口
        @app.route("/api/my-job", methods=["POST"])
        def my_job_handler():
            job = MyJob()
            return job._handle()

        # 注册健康检查接口（路径：回调接口 + "/health"）
        @app.route("/api/my-job/health", methods=["GET"])
        def my_job_health():
            job = MyJob()
            return job._health_check()
    """

    DEFAULT_ENDPOINT = "回调服务"

    @abstractmethod
    def secret_key(self) -> str:
        """
        返回密钥，用于 X-Secret 校验
        子类必须重写此方法
        """
        pass

    def endpoint(self) -> str:
        """
        返回模块名称，用于健康检查响应的 endpoint 字段
        子类应重写此方法，返回有意义的模块名称

        Returns:
            模块名称，如"数据同步服务"、"报表生成服务"等
        """
        return self.DEFAULT_ENDPOINT

    @abstractmethod
    def run_job(self, param: Dict[str, Any]) -> Result:
        """
        执行具体业务逻辑
        子类必须重写此方法
        """
        pass

    def _handle(self) -> Dict:
        """
        统一处理入口，供子类在 Flask 视图函数中调用
        """
        start_time = time.time()
        request_id = request.headers.get("X-Request-Id")

        if not request_id:
            return Result.fail(400, "缺少请求头 X-Request-Id").to_dict()

        # 运行时校验：确保 secret_key 被正确重写（非默认实现）
        try:
            secret = self.secret_key()
            if secret is None or secret == "":
                raise RuntimeError("secret_key() 未被子类正确实现，返回值为空")
        except NotImplementedError:
            raise RuntimeError("子类必须实现 secret_key() 方法")

        try:
            self._validate_secret(request)
            param = request.get_json(silent=True) or {}
            # 注入 request_id 到 param，方便业务使用（可选）
            param["request_id"] = request_id

            result = self.run_job(param)
            if result is None:
                result = Result.ok()

            result.cost_millis = int((time.time() - start_time) * 1000)
            result.request_id = request_id
            return result.to_dict()

        except SecurityException as e:
            print(f"[{request_id}] 密钥验证失败: {e}")
            return Result.fail(401, "密钥验证失败").to_dict()
        except Exception as e:
            print(f"[{request_id}] 任务执行出错: {str(e)}")
            return Result.fail(500, "执行失败").to_dict()

    def _health_check(self) -> Dict:
        """
        健康检查入口，供子类在 Flask GET 视图函数中调用

        【接口约定】
        - 路径格式：<回调接口路径>/health（强制约定）
        - 功能：仅验证 X-Secret 和 X-Request-Id 请求头，不执行业务逻辑
        - 要求：健康接口和回调接口必须使用相同的 BaseUrl

        【响应格式】
        {
            "code": 200,
            "msg": "success",
            "data": {
                "status": "UP",
                "timestamp": "2024-01-19T10:30:00",
                "endpoint": "数据同步服务",
                "message": "服务正常"
            },
            "cost_millis": 5,
            "request_id": "abc123def456"
        }
        """
        start_time = time.time()
        request_id = request.headers.get("X-Request-Id")

        # 验证请求头
        if not request_id:
            return Result.fail(400, "缺少请求头 X-Request-Id").to_dict()

        try:
            self._validate_secret(request)

            # 健康检查成功，构造响应数据
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
            return result.to_dict()

        except SecurityException as e:
            print(f"[{request_id}] 健康检查失败: 密钥验证失败")
            return Result.fail(401, "密钥验证失败").to_dict()
        except Exception as e:
            print(f"[{request_id}] 健康检查异常: {str(e)}")
            return Result.fail(500, f"健康检查异常: {str(e)}").to_dict()

    def _validate_secret(self, request):
        """验证 X-Secret 请求头"""
        input_secret = request.headers.get("X-Secret")
        expected_secret = self.secret_key()
        if input_secret != expected_secret:
            raise SecurityException("X-Secret 密钥不匹配")
