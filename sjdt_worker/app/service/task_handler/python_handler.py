import os
import tempfile
import subprocess
from datetime import datetime
from app.model.task import TaskExecuteMessage, TaskResultMessage
from app.service.task_handler.base_handler import BaseHandler
from app.utils.minio_utils import get_minio_file_object, minio_upload_file
from app.config import Config
from rocketmq.client import Producer, Message


class PythonHandler(BaseHandler):
    """Python脚本处理器"""

    def support_type(self) -> str:
        return "python"

    def handle(self, task: TaskExecuteMessage) -> bool:
        start_time = datetime.utcnow()
        start_time_str = start_time.isoformat() + "Z"
        result = TaskResultMessage(
            workerId=Config.WORKER_ID,
            taskId=task.taskId,
            taskRunId=task.taskRunId,
            status="running",
            exitCode=0,
            startTime=start_time_str,
            endTime="",
            durationMs=0,
            errorMessage=None,
            logPath=None
        )

        try:
            # 1. 解析脚本路径
            script_path = task.script.path
            if script_path.startswith("minio://"):
                script_path = script_path[8:]
            script_bucket, script_object = self._parse_minio_path(script_path)

            # 2. 从MinIO下载Python脚本到本地临时文件
            with tempfile.NamedTemporaryFile(mode='w', suffix='.py', delete=False) as f:
                script_content = get_minio_file_object(script_bucket, script_object).read().decode('utf-8')
                f.write(script_content)
                temp_script_path = f.name

            # 3. 准备日志存储
            log_bucket = "task-logs"
            log_object = f"{task.taskId}.log"

            # 4. 执行脚本（带超时控制）
            process = subprocess.Popen(
                ['python', temp_script_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True
            )

            try:
                stdout, _ = process.communicate(timeout=task.timeoutSeconds)
                exit_code = process.returncode

                # 上传日志
                self._upload_log(log_bucket, log_object, stdout)

                # 更新结果
                result = self._build_success_result(result, exit_code, log_bucket, log_object, start_time)
                self._send_task_result(result)
                return exit_code == 0

            except subprocess.TimeoutExpired:
                process.kill()
                raise TimeoutError(f"脚本执行超时（{task.timeoutSeconds}秒）")

        except Exception as e:
            import traceback
            print(f"PythonHandler 执行任务失败（{task.taskId}）：{str(e)}")
            print("错误堆栈：\n" + traceback.format_exc())  # 新增：输出堆栈信息
            result = self._build_error_result(result, str(e), start_time)
            self._send_task_result(result)
            return False
        finally:
            if 'temp_script_path' in locals() and os.path.exists(temp_script_path):
                os.unlink(temp_script_path)

    # 辅助方法
    def _parse_minio_path(self, path: str) -> tuple:
        try:
            bucket, obj = path.split('/', 1)
            return bucket, obj
        except ValueError:
            raise ValueError(f"MinIO路径格式错误：{path}")

    def _upload_log(self, bucket, obj, content):
        from io import BytesIO
        log_buffer = BytesIO(content.encode('utf-8'))
        minio_upload_file(bucket, obj, log_buffer, log_buffer.getbuffer().nbytes)

    def _build_success_result(self, result, exit_code, log_bucket, log_object, start_time):
        end_time = datetime.utcnow()
        return TaskResultMessage(
            workerId=result.workerId,
            taskId=result.taskId,
            status="success" if exit_code == 0 else "failed",
            exitCode=exit_code,
            startTime=result.startTime,
            endTime=end_time.isoformat() + "Z",
            durationMs=int((end_time - start_time).total_seconds() * 1000),
            errorMessage=None if exit_code == 0 else f"退出码：{exit_code}",
            logPath=f"{log_bucket}/{log_object}"
        )

    def _build_error_result(self, result, error_msg, start_time):
        end_time = datetime.utcnow()
        return TaskResultMessage(
            workerId=result.workerId,
            taskId=result.taskId,
            status="failed",
            exitCode=1,
            startTime=result.startTime,
            endTime=end_time.isoformat() + "Z",
            durationMs=int((end_time - start_time).total_seconds() * 1000),
            errorMessage=error_msg,
            logPath=None
        )

    def _send_task_result(self, result: TaskResultMessage):
        producer = None
        try:
            producer = Producer(Config.MQ_PRODUCER_GROUP)
            producer.set_namesrv_addr(Config.MQ_NAMESRV_ADDR)
            producer.start()

            msg = Message(Config.MQ_TASK_RESULT_TOPIC)
            msg.set_keys(result.taskId)
            msg.set_body(result.to_json().encode("utf-8"))
            producer.send_sync(msg)
        finally:
            if producer:
                producer.shutdown()

