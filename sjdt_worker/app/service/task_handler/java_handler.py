import os
import tempfile
import subprocess
from datetime import datetime
from app.model.task import TaskExecuteMessage, TaskResultMessage
from app.service.task_handler.base_handler import BaseHandler
from app.utils.minio_utils import get_minio_file_object, minio_upload_file
from app.config import Config
from rocketmq.client import Producer, Message


class JavaHandler(BaseHandler):
    """Java脚本处理器（处理.class或.jar文件）"""

    def support_type(self) -> str:
        return "java"

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

            # 2. 下载Java文件到临时目录
            temp_dir = tempfile.mkdtemp()
            file_ext = os.path.splitext(script_object)[1].lower()
            temp_file_path = os.path.join(temp_dir, f"task_{task.taskId}{file_ext}")

            with open(temp_file_path, 'wb') as f:
                f.write(get_minio_file_object(script_bucket, script_object).read())

            # 3. 准备执行命令
            if file_ext == '.jar':
                cmd = ['java', '-jar', temp_file_path]
            elif file_ext == '.class':
                class_name = os.path.splitext(os.path.basename(temp_file_path))[0]
                cmd = ['java', '-cp', temp_dir, class_name]
            else:
                raise ValueError(f"不支持的Java文件格式：{file_ext}")

            # 4. 执行命令
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True
            )

            stdout, _ = process.communicate(timeout=task.timeoutSeconds)
            exit_code = process.returncode

            # 5. 处理结果
            log_bucket = "task-logs"
            log_object = f"{task.taskId}/{datetime.now().strftime('%Y%m%d%H%M%S')}.log"
            self._upload_log(log_bucket, log_object, stdout)

            result = self._build_success_result(result, exit_code, log_bucket, log_object, start_time)
            self._send_task_result(result)
            return exit_code == 0

        except Exception as e:
            result = self._build_error_result(result, str(e), start_time)
            self._send_task_result(result)
            return False
        finally:
            if 'temp_dir' in locals() and os.path.exists(temp_dir):
                import shutil
                shutil.rmtree(temp_dir, ignore_errors=True)

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
