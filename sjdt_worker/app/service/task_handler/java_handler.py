import os
import tempfile
import subprocess
import threading
import shutil
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


class JavaHandler(BaseHandler):
    """Java脚本处理器（处理.class或.jar文件）"""

    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=10, thread_name_prefix="JavaHandler")

    def support_type(self) -> str:
        return "java"

    def handle(self, task: TaskExecuteMessage) -> bool:
        """
        异步处理Java任务
        """
        try:
            logger.info("开始异步执行Java脚本，任务ID：%s", task.taskId)
            
            # 创建任务实例
            task_instance_manager.create_instance(task.taskRunId, {
                "taskId": task.taskId,
                "workerId": Config.WORKER_ID,
                "scriptType": task.scriptType
            })
            
            # 在线程池中异步执行任务
            future = self.executor.submit(self._execute_task_async, task)
            
            # 立即返回true表示消息已接收并开始处理
            return True
            
        except Exception as e:
            logger.error("JavaHandler 提交任务失败（%s）：%s", task.taskId, str(e), exc_info=True)
            # 更新任务状态为失败
            task_instance_manager.update_status(task.taskRunId, "failed", 
                                               error_message=str(e))
            return False

    def _execute_task_async(self, task: TaskExecuteMessage):
        """异步执行任务的具体实现"""
        start_time = datetime.utcnow()
        task_run_id = task.taskRunId
        
        try:
            # 更新任务状态为运行中
            task_instance_manager.update_status(task_run_id, "running")
            
            logger.info("开始执行Java脚本，任务ID：%s", task.taskId)
            
            # 1. 解析脚本路径
            script_bucket = Config.MINIO_BUCKET_NAME
            script_object = task.script.path
            if script_object.startswith("minio://"):
                # 如果是完整的 minio://bucket/path 格式，则解析
                path_parts = script_object[8:].split('/', 1)
                if len(path_parts) == 2:
                    script_bucket, script_object = path_parts
                else:
                    script_object = path_parts[0]

            # 2. 下载Java文件到临时目录
            temp_dir = tempfile.mkdtemp()
            original_filename = os.path.basename(script_object)
            file_ext = os.path.splitext(original_filename)[1].lower()
            temp_file_path = os.path.join(temp_dir, original_filename)

            logger.info("从MinIO下载Java文件，bucket：%s，object：%s", script_bucket, script_object)
            from app.utils.minio_utils import get_minio_file_object
            
            with open(temp_file_path, 'wb') as f:
                f.write(get_minio_file_object(script_bucket, script_object).read())

            # 3. 准备日志存储
            log_bucket = Config.MINIO_BUCKET_NAME
            log_object = f"task-logs/{task.taskId}/{task_run_id}.log"
            
            # 更新任务实例的日志路径
            task_instance_manager.update_status(task_run_id, "running",
                                             log_bucket=log_bucket,
                                             log_object=log_object)

            # 4. 准备执行命令
            if file_ext == '.jar':
                cmd = ['java', '-jar', temp_file_path]
            elif file_ext == '.class':
                # 对于.class文件，类名必须与文件名一致（不含扩展名）
                class_name = os.path.splitext(original_filename)[0]
                cmd = ['java', '-cp', temp_dir, class_name]
            else:
                raise ValueError(f"不支持的Java文件格式：{file_ext}")

            # 5. 启动子进程执行脚本
            timeout_seconds = task.timeoutSeconds if task.timeoutSeconds > 0 else 300
            logger.info("开始执行Java命令，超时时间：%d秒", timeout_seconds)
            
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,  # 行缓冲
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
                'temp_dir': temp_dir
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
                args=(process, task, task_run_id, start_time, log_bucket, log_object, temp_dir),
                daemon=True
            )
            status_thread.start()
            
        except Exception as e:
            logger.error("异步任务执行失败（%s）：%s", task.taskId, str(e), exc_info=True)
            self._handle_task_error(task, task_run_id, str(e), start_time)

    def _monitor_log_output(self, process, log_bucket: str, log_object: str, task_run_id: str):
        """监控并实时上传日志输出"""
        try:
            logger.info("启动日志监控线程，taskRunId: %s", task_run_id)
            
            # 创建本地日志缓存文件
            local_log_path = f"/tmp/task_{task_run_id}_logs.txt"
            
            log_content = []
            for line in iter(process.stdout.readline, ''):
                if line:
                    log_content.append(line)
                    # 将日志写入本地缓存
                    with open(local_log_path, 'a', encoding='utf-8') as f:
                        f.write(line)
                    # 每累积5行或遇到换行符时上传日志
                    if len(log_content) >= 5 or line.endswith('\n'):
                        self._upload_partial_log_with_retry(log_bucket, log_object, ''.join(log_content), local_log_path)
                        log_content = []
                
                # 检查进程是否结束
                if process.poll() is not None:
                    break
            
            # 上传剩余的日志内容
            if log_content:
                self._upload_partial_log_with_retry(log_bucket, log_object, ''.join(log_content), local_log_path)
                
            logger.info("日志监控完成，taskRunId: %s", task_run_id)
            
            # 删除本地日志缓存
            import os
            if os.path.exists(local_log_path):
                os.remove(local_log_path)
                logger.info("本地日志缓存已删除，taskRunId: %s", task_run_id)
            
        except Exception as e:
            logger.error("日志监控异常，taskRunId: %s，错误: %s", task_run_id, str(e), exc_info=True)

    def _upload_partial_log_with_retry(self, bucket: str, obj: str, content: str, local_log_path: str, max_retries: int = 3):
        """带重试机制的日志追加上传"""
        for attempt in range(max_retries):
            try:
                self._append_log_to_minio(bucket, obj, content)
                return  # 成功则返回
            except Exception as e:
                logger.error("日志上传失败（尝试 %d/%d），taskRunId: %s，错误: %s", 
                           attempt + 1, max_retries, obj.split('/')[-1].split('.')[0], str(e))
                if attempt < max_retries - 1:
                    # 指数退避重试
                    import time
                    time.sleep(2 ** attempt)
                else:
                    logger.error("日志上传最终失败，将仅保存在本地: %s", local_log_path)

    def _append_log_to_minio(self, bucket: str, obj: str, content: str):
        """实现真正的日志追加上传"""
        from io import BytesIO
        try:
            # 获取MinIO客户端
            minio_client = get_minio_client()
            
            # 检查文件是否存在
            try:
                # 尝试获取现有文件
                existing_obj = minio_client.get_object(bucket, obj)
                existing_content = existing_obj.read().decode('utf-8')
                existing_obj.close()
                existing_obj.release_conn()
                
                # 追加新内容
                combined_content = existing_content + content
                log_buffer = BytesIO(combined_content.encode('utf-8'))
                log_buffer.seek(0)
            except Exception:
                # 文件不存在，直接上传新内容
                log_buffer = BytesIO(content.encode('utf-8'))
            
            # 上传完整内容
            minio_upload_file(bucket, obj, log_buffer, log_buffer.getbuffer().nbytes)
            logger.debug("日志上传成功，追加 %d 字节，bucket: %s, obj: %s", 
                       len(content), bucket, obj)
        except Exception as e:
            logger.error("日志追加上传失败: %s", str(e))
            raise

    def _monitor_process_status(self, process, task: TaskExecuteMessage, task_run_id: str, 
                             start_time: datetime, log_bucket: str, log_object: str, 
                             temp_dir: str):
        """监控进程状态变化"""
        try:
            logger.info("启动状态监控线程，taskRunId: %s", task_run_id)
            
            # 等待进程完成
            exit_code = process.wait()
            
            # 读取所有剩余的输出
            remaining_output = process.stdout.read() if process.stdout else ""
            
            # 定义本地日志路径
            local_log_path = f"/tmp/task_{task_run_id}_logs.txt"

            # 上传最终的完整日志
            if remaining_output:
                self._upload_partial_log_with_retry(log_bucket, log_object, remaining_output, local_log_path)
            
            # 构建最终结果
            end_time = datetime.utcnow()
            duration_ms = int((end_time - start_time).total_seconds() * 1000)
            
            # 发送最终结果
            result = TaskResultMessage(
                workerId=Config.WORKER_ID,
                taskId=task.taskId,
                taskRunId=task_run_id,
                status="success" if exit_code == 0 else "failed",
                exitCode=exit_code,
                startTime=start_time.isoformat() + "Z",
                endTime=end_time.isoformat() + "Z",
                durationMs=duration_ms,
                errorMessage=None if exit_code == 0 else f"退出码：{exit_code}",
                logPath=f"{log_bucket}/{log_object}"
            )
            
            # 发送结果消息
            self._send_task_result(result)
            
            # 更新任务实例状态
            task_instance_manager.update_status(
                task_run_id, 
                "success" if exit_code == 0 else "failed",
                exit_code=exit_code,
                duration_ms=duration_ms
            )
            
            # 从进程管理器中移除
            process_manager.remove_process(task_run_id)
            
            logger.info("任务执行完成，taskRunId: %s，状态: %s，耗时: %dms", 
                      task_run_id, result.status, duration_ms)
                      
        except Exception as e:
            logger.error("状态监控异常，taskRunId: %s，错误: %s", task_run_id, str(e), exc_info=True)
            self._handle_task_error(task, task_run_id, str(e), start_time)
        finally:
            # 清理临时目录
            if os.path.exists(temp_dir):
                shutil.rmtree(temp_dir, ignore_errors=True)

    def _handle_task_error(self, task: TaskExecuteMessage, task_run_id: str, 
                          error_msg: str, start_time: datetime):
        """处理任务执行错误"""
        try:
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
            
            # 发送错误结果
            self._send_task_result(result)
            
            # 更新任务实例状态
            task_instance_manager.update_status(
                task_run_id, 
                "failed",
                error_message=error_msg,
                duration_ms=duration_ms
            )
            
            # 从进程管理器中移除
            process_manager.remove_process(task_run_id)
            
        except Exception as e:
            logger.error("处理任务错误时发生异常，taskRunId: %s，错误: %s", task_run_id, str(e))

    def _send_task_result(self, result: TaskResultMessage):
        """发送任务结果到MQ"""
        producer = None
        try:
            producer = Producer(Config.MQ_PRODUCER_GROUP)
            producer.set_namesrv_addr(Config.MQ_NAMESRV_ADDR)
            producer.start()

            msg = Message(Config.MQ_TASK_RESULT_TOPIC)
            msg.set_keys(result.taskId)
            msg.set_body(result.to_json().encode("utf-8"))
            producer.send_sync(msg)
            logger.debug("任务结果已发送，taskId: %s，状态: %s", result.taskId, result.status)
        finally:
            if producer:
                producer.shutdown()
