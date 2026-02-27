import os
import tempfile
import subprocess
import threading
import time
import uuid
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


class PythonHandler(BaseHandler):
    """Python脚本处理器"""

    def __init__(self):
        self.executor = ThreadPoolExecutor(max_workers=10, thread_name_prefix="PythonHandler")

    def support_type(self) -> str:
        return "python"

    def handle(self, task: TaskExecuteMessage) -> bool:
        """
        异步处理Python任务
        """
        try:
            logger.info("开始异步执行Python脚本，任务ID：%s", task.taskId)
            
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
            logger.error("PythonHandler 提交任务失败（%s）：%s", task.taskId, str(e), exc_info=True)
            # 更新任务状态为失败
            task_instance_manager.update_status(task.taskRunId, "failed", 
                                               error_message=str(e))
            return False

    def _execute_task_async(self, task: TaskExecuteMessage):
        """异步执行任务的具体实现"""
        start_time = datetime.utcnow()
        task_run_id = task.taskRunId
        
        try:
            logger.info("开始执行Python脚本，任务ID：%s，脚本路径：%s，taskRunId：%s", task.taskId, task.script.path, task_run_id)
            
            # 1. 验证环境依赖
            self._validate_dependencies(task, task_run_id)
            
            # 2. 下载脚本到固定目录
            script_bucket = Config.MINIO_BUCKET_NAME
            script_object = task.script.path
            
            logger.info("从MinIO下载脚本，bucket：%s，object：%s", script_bucket, script_object)
            
            # 确保scripts_tmp目录存在
            scripts_dir = "/app/app/scripts_tmp"
            os.makedirs(scripts_dir, exist_ok=True)
            
            # 判断是否需要下载整个目录
            root_path = getattr(task.script, 'root_path', None)
            is_directory = getattr(task.script, 'is_directory', False)
            parent_id = getattr(task.script, 'parent_id', None)
            
            # 日志输出调试信息
            logger.info("脚本信息: name=%s, path=%s, root_path=%s, is_directory=%s, parent_id=%s",
                getattr(task.script, 'name', None), script_object, root_path, is_directory, parent_id)
            
            # 需要下载目录的情况：
            # 1. 有root_path且不是目录类型，且root_path不是根目录
            # 2. 或者是目录类型本身
            # 3. 或者有parent_id（说明在某个目录下，需要下载整个父目录）
            need_download_dir = (root_path and root_path != '/' and not is_directory) or is_directory or parent_id
            
            temp_script_path = None
            if need_download_dir:
                # 有根目录路径：下载整个文件夹
                from app.utils.minio_utils import download_directory
                
                # 确定要下载的目录前缀
                if is_directory:
                    # 本身是目录，下载整个目录
                    folder_prefix = script_object
                elif root_path:
                    # 有root_path，使用root_path
                    folder_prefix = root_path
                else:
                    # 没有root_path但有parent_id，从脚本路径中提取父目录
                    folder_prefix = script_object.rsplit('/', 1)[0] + '/'
                
                if folder_prefix.startswith(f"{script_bucket}/"):
                    folder_prefix = folder_prefix[len(script_bucket) + 1:]
                
                # 使用目录名作为本地目录名
                dir_name = folder_prefix.rstrip('/').split('/')[-1]
                if not dir_name:
                    dir_name = 'root'
                temp_dir = os.path.join(scripts_dir, dir_name)
                
                logger.info("开始下载目录，prefix：%s，local_dir：%s", folder_prefix, temp_dir)
                file_count = download_directory(script_bucket, folder_prefix, temp_dir)
                logger.info("目录下载完成，共 %d 个文件", file_count)
                
                # 根据脚本路径确定具体执行文件
                script_relative_path = script_object
                if script_object.startswith(f"{script_bucket}/"):
                    script_relative_path = script_object[len(script_bucket) + 1:]
                if script_relative_path.startswith(folder_prefix):
                    script_relative_path = script_relative_path[len(folder_prefix):]
                
                temp_script_path = os.path.join(temp_dir, script_relative_path)
                logger.info("入口文件路径：%s", temp_script_path)
            else:
                # 文件类型：下载单个文件，使用原始脚本名
                script_name = getattr(task.script, 'name', 'script')
                # 确保有正确的扩展名
                if not script_name.endswith('.py'):
                    script_name = f"{script_name}.py"
                unique_filename = script_name
                temp_script_path = os.path.join(scripts_dir, unique_filename)
                
                # 从MinIO下载脚本内容
                from app.utils.minio_utils import get_minio_file_object
                script_content = get_minio_file_object(script_bucket, script_object).read().decode('utf-8')
                
                # 保存到固定目录
                with open(temp_script_path, 'w', encoding='utf-8') as f:
                    f.write(script_content)
                
                logger.info("脚本下载成功，保存到：%s，文件大小：%d字节", temp_script_path, len(script_content))

            # 3. 准备日志存储
            log_bucket = Config.MINIO_BUCKET_NAME
            log_object = f"task-logs/{task.taskId}/{task_run_id}.log"
            
            logger.info("日志存储准备完成，bucket：%s，object：%s", log_bucket, log_object)
            
            # 4. 更新任务状态为运行中（包含日志路径）
            task_instance_manager.update_status(task_run_id, "running",
                                             log_bucket=log_bucket,
                                             log_object=log_object)
            logger.info("任务状态已更新为运行中，taskRunId：%s", task_run_id)

            # 5. 启动子进程执行脚本
            timeout_seconds = task.timeoutSeconds if task.timeoutSeconds > 0 else 300
            logger.info("开始执行脚本，超时时间：%d秒", timeout_seconds)
            
            # 复制工具类到脚本所在目录
            utils_dir = "/app/app/scripts_tmp/utils"
            script_dir = os.path.dirname(temp_script_path)
            copied_utils_dir = None
            if os.path.exists(utils_dir):
                copied_utils_dir = os.path.join(script_dir, "utils")
                if os.path.exists(copied_utils_dir):
                    shutil.rmtree(copied_utils_dir)
                shutil.copytree(utils_dir, copied_utils_dir)
                logger.info("工具类已复制到: %s", copied_utils_dir)
            
            process = subprocess.Popen(
                ['python3', temp_script_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,  # 行缓冲
                universal_newlines=True,
                start_new_session=True,
                cwd=os.path.dirname(temp_script_path)
            )
            
            logger.info("子进程已启动，PID：%d", process.pid)
            
            # 4. 将进程添加到进程管理器
            process_info = {
                'task': task,
                'process': process,
                'start_time': start_time,
                'log_bucket': log_bucket,
                'log_object': log_object,
                'temp_script_path': temp_script_path
            }
            process_manager.add_process(task_run_id, process_info)
            logger.info("进程已添加到进程管理器，taskRunId：%s", task_run_id)
            
            # 5. 启动日志监控线程
            log_thread = threading.Thread(
                target=self._monitor_log_output,
                args=(process, log_bucket, log_object, task_run_id),
                daemon=True
            )
            log_thread.start()
            logger.info("日志监控线程已启动，threadName：%s", log_thread.name)
            
            # 6. 启动状态监控线程
            status_thread = threading.Thread(
                target=self._monitor_process_status,
                args=(process, task, task_run_id, start_time, log_bucket, log_object, temp_script_path, copied_utils_dir),
                daemon=True
            )
            status_thread.start()
            logger.info("状态监控线程已启动，threadName：%s", status_thread.name)
            
        except Exception as e:
            logger.error("异步任务执行失败（%s）：%s", task.taskId, str(e), exc_info=True)
            self._handle_task_error(task, task_run_id, str(e), start_time)

    def _monitor_log_output(self, process, log_bucket: str, log_object: str, task_run_id: str):
        """监控并实时上传日志输出"""
        try:
            logger.info("启动日志监控线程，taskRunId: %s", task_run_id)
            
            import time
            import os
            
            local_log_path = f"/tmp/task_{task_run_id}_logs.txt"
            start_time = time.time()
            last_upload_time = start_time
            
            def get_upload_interval(elapsed):
                if elapsed < 10:
                    return 1
                elif elapsed < 60:
                    return 3
                else:
                    return 5
            
            def upload_log_file():
                try:
                    if os.path.exists(local_log_path):
                        with open(local_log_path, 'r', encoding='utf-8') as f:
                            content = f.read()
                        if content:
                            from io import BytesIO
                            from app.utils.minio_utils import minio_upload_file
                            log_buffer = BytesIO(content.encode('utf-8'))
                            minio_upload_file(log_bucket, log_object, log_buffer, len(content))
                            logger.debug("日志上传成功，bucket: %s, obj: %s", log_bucket, log_object)
                except Exception as e:
                    logger.error("日志上传失败: %s", str(e))
            
            while True:
                line = process.stdout.readline()
                if not line:
                    break
                
                with open(local_log_path, 'a', encoding='utf-8') as f:
                    f.write(line)
                
                current_time = time.time()
                elapsed = current_time - start_time
                interval = get_upload_interval(elapsed)
                
                if current_time - last_upload_time >= interval:
                    upload_log_file()
                    last_upload_time = current_time
                
                if process.poll() is not None:
                    break
            
            upload_log_file()
            logger.info("日志监控完成，taskRunId: %s", task_run_id)
            
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
                             temp_script_path: str, copied_utils_dir: str = None):
        """监控进程状态变化"""
        try:
            logger.info("启动状态监控线程，taskRunId: %s", task_run_id)
            
            # 等待进程完成
            exit_code = process.wait()
            
            # 读取所有剩余的输出（包括stdout和stderr，因为stderr已经重定向到stdout）
            remaining_stdout = process.stdout.read() if process.stdout else ""
            
            # 收集所有输出
            all_output = remaining_stdout
            
            # 定义本地日志路径
            local_log_path = f"/tmp/task_{task_run_id}_logs.txt"
            
            # 上传最终的完整日志
            if all_output:
                self._upload_partial_log_with_retry(log_bucket, log_object, all_output, local_log_path)
            
            # 构建最终结果
            end_time = datetime.utcnow()
            duration_ms = int((end_time - start_time).total_seconds() * 1000)
            
            # 构建错误信息，包含具体输出
            error_message = None
            if exit_code != 0:
                # 将完整输出作为错误信息，限制长度为1000字符
                error_message = all_output[:1000] if all_output else f"退出码：{exit_code}"
            
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
                errorMessage=error_message,
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
            # 清理临时文件
            if os.path.exists(temp_script_path):
                os.unlink(temp_script_path)
            
            # 清理复制的工具类目录
            if copied_utils_dir and os.path.exists(copied_utils_dir):
                shutil.rmtree(copied_utils_dir)
                logger.info("工具类目录已清理: %s", copied_utils_dir)

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

    def _validate_dependencies(self, task: TaskExecuteMessage, task_run_id: str):
        """
        验证Worker环境是否满足任务依赖要求
        :param task: 任务消息
        :param task_run_id: 任务运行ID
        :raises Exception: 依赖不满足时抛出异常
        """
        from app.utils.env_utils import EnvUtils
        
        # 获取脚本的依赖标签
        script_tags = task.script.tags if task.script else []
        
        if not script_tags:
            logger.info("任务 %s 无依赖标签要求，跳过依赖验证", task.taskId)
            return
        
        logger.info("开始验证任务 %s 的依赖要求，任务RunId: %s", task.taskId, task_run_id)
        
        # 获取Worker的已安装包列表
        worker_packages = {pkg['name'].lower(): pkg['version'] for pkg in Config.TAGS}
        
        missing_deps = []
        version_mismatch = []
        
        for tag in script_tags:
            if not tag.name:
                continue
                
            pkg_name = tag.name.lower()
            
            # 检查包是否已安装
            if pkg_name not in worker_packages:
                missing_deps.append(tag)
                logger.warning("缺少必需依赖：%s（要求：%s %s %s）", 
                              tag.name, tag.operator or '>=', tag.version or 'any', '')
                continue
            
            # 检查版本约束
            if tag.version and tag.operator:
                current_version = worker_packages[pkg_name]
                is_valid, actual_version = EnvUtils.check_package_version(
                    tag.name, tag.version, tag.operator
                )
                if not is_valid:
                    version_mismatch.append({
                        'name': tag.name,
                        'required': f"{tag.operator} {tag.version}",
                        'actual': actual_version
                    })
                    logger.warning("版本不匹配：%s（要求：%s %s，实际：%s）",
                                  tag.name, tag.operator, tag.version, actual_version)
        
        # 构建错误信息
        error_messages = []
        
        if missing_deps:
            deps_str = ", ".join([f"{t.name} {t.operator or '>='} {t.version or 'any'}" 
                                  for t in missing_deps])
            error_messages.append(f"缺少必需依赖：{deps_str}")
        
        if version_mismatch:
            versions_str = ", ".join([f"{v['name']}（要求：{v['required']}，实际：{v['actual']}）" 
                                     for v in version_mismatch])
            error_messages.append(f"版本不满足：{versions_str}")
        
        if error_messages:
            error_msg = "; ".join(error_messages)
            logger.error("依赖验证失败：%s", error_msg)
            
            # 更新任务状态为失败
            task_instance_manager.update_status(
                task_run_id, 
                "failed",
                error_message=f"依赖验证失败：{error_msg}"
            )
            
            raise Exception(f"依赖验证失败：{error_msg}")
        
        logger.info("任务 %s 依赖验证通过，任务RunId: %s", task.taskId, task_run_id)

