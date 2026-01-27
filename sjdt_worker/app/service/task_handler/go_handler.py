import os
import tempfile
import subprocess
import threading
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor
from app.model.task import TaskExecuteMessage, TaskResultMessage
from app.service.task_handler.base_handler import BaseHandler
from app.service.process_manager import process_manager
from app.service.task_instance_manager import task_instance_manager
from app.utils.minio_utils import minio_upload_file, get_minio_client
from app.config import Config
from rocketmq.client import Producer, Message
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class GoHandler(BaseHandler):
    """Go任务处理器（执行编译后的二进制文件，无需Go环境）"""

    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=10, thread_name_prefix="GoHandler")

    def support_type(self) -> str:
        return "go"

    def handle(self, task: TaskExecuteMessage) -> bool:
        """
        异步处理Go任务（执行二进制文件）
        """
        try:
            logger.info("开始异步执行Go二进制任务，任务ID：%s", task.taskId)
            
            # 创建任务实例
            task_instance_manager.create_instance(task.taskRunId, {
                "taskId": task.taskId,
                "workerId": Config.WORKER_ID,
                "scriptType": task.scriptType
            })
            
            # 在线程池中异步执行任务
            self.executor.submit(self._execute_task_async, task)
            
            return True
            
        except Exception as e:
            logger.error("GoHandler 提交任务失败（%s）：%s", task.taskId, str(e), exc_info=True)
            task_instance_manager.update_status(task.taskRunId, "failed", 
                                               error_message=str(e))
            return False

    def _execute_task_async(self, task: TaskExecuteMessage):
        """异步执行任务的具体实现"""
        start_time = datetime.utcnow()
        task_run_id = task.taskRunId
        
        try:
            logger.info("开始执行Go二进制文件，任务ID：%s，路径：%s，taskRunId：%s", task.taskId, task.script.path, task_run_id)
            
            # 1. 下载二进制文件到临时目录
            script_bucket = Config.MINIO_BUCKET_NAME
            script_object = task.script.path
            
            # 创建临时文件
            with tempfile.NamedTemporaryFile(delete=False) as f:
                from app.utils.minio_utils import get_minio_file_object
                f.write(get_minio_file_object(script_bucket, script_object).read())
                temp_bin_path = f.name
            
            # 2. 赋予执行权限
            os.chmod(temp_bin_path, 0o755)
            logger.info("二进制文件下载成功并赋予执行权限：%s", temp_bin_path)

            # 3. 准备日志存储
            log_bucket = Config.MINIO_BUCKET_NAME
            log_object = f"task-logs/{task.taskId}/{task_run_id}.log"
            
            # 4. 更新任务状态为运行中
            task_instance_manager.update_status(task_run_id, "running",
                                             log_bucket=log_bucket,
                                             log_object=log_object)

            # 5. 启动子进程执行程序
            timeout_seconds = task.timeoutSeconds if task.timeoutSeconds > 0 else 300
            logger.info("开始运行二进制程序，超时时间：%d秒", timeout_seconds)
            
            process = subprocess.Popen(
                [temp_bin_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                universal_newlines=True,
                start_new_session=True
            )
            
            # 6. 将进程添加到进程管理器
            process_info = {
                'task': task,
                'process': process,
                'start_time': start_time,
                'log_bucket': log_bucket,
                'log_object': log_object,
                'temp_bin_path': temp_bin_path
            }
            process_manager.add_process(task_run_id, process_info)
            
            # 7. 启动日志监控线程
            log_thread = threading.Thread(
                target=self._monitor_log_output,
                args=(process, log_bucket, log_object, task_run_id),
                daemon=True
            )
            log_thread.start()
            
            # 8. 启动状态监控线程
            status_thread = threading.Thread(
                target=self._monitor_process_status,
                args=(process, task, task_run_id, start_time, log_bucket, log_object, temp_bin_path),
                daemon=True
            )
            status_thread.start()
            
        except Exception as e:
            logger.error("异步Go任务执行失败（%s）：%s", task.taskId, str(e), exc_info=True)
            self._handle_task_error(task, task_run_id, str(e), start_time)

    def _monitor_log_output(self, process, log_bucket: str, log_object: str, task_run_id: str):
        """监控并实时上传日志输出（逻辑同PythonHandler）"""
        try:
            local_log_path = f"/tmp/task_{task_run_id}_logs.txt"
            log_content = []
            for line in iter(process.stdout.readline, ''):
                if line:
                    log_content.append(line)
                    with open(local_log_path, 'a', encoding='utf-8') as f:
                        f.write(line)
                    if len(log_content) >= 5 or line.endswith('\n'):
                        self._upload_partial_log_with_retry(log_bucket, log_object, ''.join(log_content), local_log_path)
                        log_content = []
                if process.poll() is not None:
                    break
            
            if log_content:
                self._upload_partial_log_with_retry(log_bucket, log_object, ''.join(log_content), local_log_path)
            
            if os.path.exists(local_log_path):
                os.remove(local_log_path)
        except Exception as e:
            logger.error("Go任务日志监控异常: %s", str(e))

    def _upload_partial_log_with_retry(self, bucket: str, obj: str, content: str, local_log_path: str, max_retries: int = 3):
        """带重试机制的日志追加上传"""
        for attempt in range(max_retries):
            try:
                self._append_log_to_minio(bucket, obj, content)
                return  # 成功则返回
            except Exception as e:
                logger.error("Go日志上传失败（尝试 %d/%d），taskRunId: %s，错误: %s", 
                           attempt + 1, max_retries, obj.split('/')[-1].split('.')[0], str(e))
                if attempt < max_retries - 1:
                    import time
                    time.sleep(2 ** attempt)
                else:
                    logger.error("Go日志上传最终失败，将仅保存在本地: %s", local_log_path)

    def _append_log_to_minio(self, bucket: str, obj: str, content: str):
        from io import BytesIO
        try:
            minio_client = get_minio_client()
            try:
                existing_obj = minio_client.get_object(bucket, obj)
                existing_content = existing_obj.read().decode('utf-8')
                existing_obj.close()
                existing_obj.release_conn()
                combined_content = existing_content + content
                log_buffer = BytesIO(combined_content.encode('utf-8'))
            except Exception:
                log_buffer = BytesIO(content.encode('utf-8'))
            
            minio_upload_file(bucket, obj, log_buffer, log_buffer.getbuffer().nbytes)
        except Exception as e:
            logger.error("Go日志上传失败: %s", str(e))

    def _monitor_process_status(self, process, task, task_run_id, start_time, log_bucket, log_object, temp_bin_path):
        """监控进程状态逻辑同PythonHandler"""
        try:
            exit_code = process.wait()
            # 读取所有剩余的输出
            remaining_output = process.stdout.read() if process.stdout else ""
            
            # 定义本地日志路径
            local_log_path = f"/tmp/task_{task_run_id}_logs.txt"

            if remaining_output:
                self._upload_partial_log_with_retry(log_bucket, log_object, remaining_output, local_log_path)
            
            end_time = datetime.utcnow()
            duration_ms = int((end_time - start_time).total_seconds() * 1000)
            
            error_message = None
            if exit_code != 0:
                error_message = remaining_output[:1000] if remaining_output else f"退出码：{exit_code}"
            
            result = TaskResultMessage(
                workerId=Config.WORKER_ID,
                taskId=task.taskId,
                taskRunId=task_run_id,
                status="success" if exit_code == 0 else "failed",
                exitCode=exit_code,
                startTime=start_time.isoformat() + "Z",
                endTime=end_time.isoformat() + "Z",
                durationMs=duration_ms,
                errorMessage=error_message,
                logPath=f"{log_bucket}/{log_object}"
            )
            
            self._send_task_result(result)
            task_instance_manager.update_status(task_run_id, result.status, exit_code=exit_code, duration_ms=duration_ms)
            process_manager.remove_process(task_run_id)
            
        except Exception as e:
            logger.error("Go任务状态监控异常: %s", str(e))
        finally:
            if os.path.exists(temp_bin_path):
                os.unlink(temp_bin_path)

    def _handle_task_error(self, task, task_run_id, error_msg, start_time):
        end_time = datetime.utcnow()
        duration_ms = int((end_time - start_time).total_seconds() * 1000)
        result = TaskResultMessage(
            workerId=Config.WORKER_ID,
            taskId=task.taskId,
            taskRunId=task_run_id,
            status="failed",
            exitCode=1,
            startTime=start_time.isoformat() + "Z",
            endTime=end_time.isoformat() + "Z",
            durationMs=duration_ms,
            errorMessage=error_msg,
            logPath=None
        )
        self._send_task_result(result)
        task_instance_manager.update_status(task_run_id, "failed", error_message=error_msg, duration_ms=duration_ms)
        process_manager.remove_process(task_run_id)

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
