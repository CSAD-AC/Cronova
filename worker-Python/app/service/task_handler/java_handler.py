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
        """异步处理Java任务"""
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
            
            # 1. 验证环境依赖
            self._validate_dependencies(task, task_run_id)
            
            # 2. 解析脚本路径
            script_bucket = Config.MINIO_BUCKET_NAME
            script_object = task.script.path
            if script_object.startswith("minio://"):
                path_parts = script_object[8:].split('/', 1)
                if len(path_parts) == 2:
                    script_bucket, script_object = path_parts
                else:
                    script_object = path_parts[0]

            # 获取根目录路径
            root_path = getattr(task.script, 'root_path', None)
            is_directory = getattr(task.script, 'is_directory', False)
            parent_id = getattr(task.script, 'parent_id', None)
            main_file = getattr(task.script, 'main_file', None)
            
            logger.info("脚本信息: name=%s, path=%s, root_path=%s, is_directory=%s, parent_id=%s, main_file=%s",
                getattr(task.script, 'name', None), script_object, root_path, is_directory, parent_id, main_file)

            # 需要下载目录的情况：
            # 1. 本身是目录类型（is_directory=True）
            # 2. 或者有父目录ID（parent_id 存在），说明是文件夹中的文件
            # 注意：不再依赖 root_path 来判断，因为单文件时 root_path 也会有值
            need_download_dir = is_directory or parent_id
            
            # 确保scripts_tmp目录存在
            scripts_dir = "/app/app/scripts_tmp"
            os.makedirs(scripts_dir, exist_ok=True)
            
            if need_download_dir:
                # 下载整个文件夹
                from app.utils.minio_utils import download_directory
                
                # 确定要下载的目录前缀
                if is_directory:
                    # 本身是目录，下载整个目录
                    folder_prefix = script_object
                else:
                    # 有 parent_id，使用 root_path
                    # root_path 在这种情况下应该是父目录的路径
                    folder_prefix = root_path if root_path else script_object.rsplit('/', 1)[0] + '/'
                
                if folder_prefix.startswith(f"{script_bucket}/"):
                    folder_prefix = folder_prefix[len(script_bucket) + 1:]
                
                # 使用目录名作为本地目录名（统一命名逻辑）
                dir_name = folder_prefix.rstrip('/').split('/')[-1]
                if not dir_name:
                    dir_name = 'root'
                temp_dir = os.path.join(scripts_dir, dir_name)
                
                logger.info("开始下载Java目录，prefix：%s，local_dir：%s", folder_prefix, temp_dir)
                file_count = download_directory(script_bucket, folder_prefix, temp_dir)
                logger.info("目录下载完成，共 %d 个文件", file_count)
                
                # 优先使用 main_file 确定入口文件，否则使用脚本路径
                if main_file:
                    entry_file = main_file
                    if entry_file.startswith('/'):
                        entry_file = entry_file[1:]
                    temp_file_path = os.path.join(temp_dir, entry_file)
                else:
                    script_relative_path = script_object
                    if script_object.startswith(f"{script_bucket}/"):
                        script_relative_path = script_object[len(script_bucket) + 1:]
                    if script_relative_path.startswith(folder_prefix):
                        script_relative_path = script_relative_path[len(folder_prefix):]
                    temp_file_path = os.path.join(temp_dir, script_relative_path)
                
                original_filename = os.path.basename(temp_file_path)
                file_ext = os.path.splitext(original_filename)[1].lower()
            else:
                # 单文件类型：使用随机临时目录
                temp_dir = tempfile.mkdtemp()
                
                original_filename = os.path.basename(script_object)
                file_ext = os.path.splitext(original_filename)[1].lower()
                temp_file_path = os.path.join(temp_dir, original_filename)

                logger.info("从MinIO下载Java文件，bucket：%s，object：%s", script_bucket, script_object)
                from app.utils.minio_utils import get_minio_file_object
                
                with open(temp_file_path, 'wb') as f:
                    f.write(get_minio_file_object(script_bucket, script_object).read())

            # 4. 准备日志存储
            log_bucket = Config.MINIO_BUCKET_NAME
            log_object = f"task-logs/{task.taskId}/{task_run_id}.log"
            
            # 更新任务实例的日志路径
            task_instance_manager.update_status(task_run_id, "running",
                                             log_bucket=log_bucket,
                                             log_object=log_object)

            # 5. 自动检测并加载配置文件
            config_files = self._detect_config_files(temp_dir)
            
            # 4. 准备执行命令
            if file_ext == '.jar':
                cmd = ['java', '-jar', temp_file_path]
            elif file_ext == '.class':
                # 对于.class文件，类名必须与文件名一致（不含扩展名）
                class_name = os.path.splitext(original_filename)[0]
                cmd = ['java', '-cp', temp_dir, class_name]
            else:
                raise ValueError(f"不支持的Java文件格式：{file_ext}")
            
            # 如果检测到配置文件，添加JVM参数
            if config_files:
                logger.info("检测到配置文件：%s，将添加到JVM参数中", config_files)
                # 添加 Spring 配置参数
                config_params = []
                for cf in config_files:
                    if cf.endswith('.yml') or cf.endswith('.yaml'):
                        config_params.append(f"file:{cf}")
                    elif cf.endswith('.properties'):
                        config_params.append(f"file:{cf}")
                
                if config_params:
                    # 插入到命令开头（JVM参数位置）
                    config_arg = "--spring.config.location=" + ",".join(config_params)
                    cmd = cmd[0:1] + [config_arg] + cmd[1:]

            # 5. 启动子进程执行脚本（注入 SJDT_SHARD_* 分片环境变量，业务脚本按分片约定切分数据）
            timeout_seconds = task.timeoutSeconds if task.timeoutSeconds > 0 else 300
            logger.info("开始执行Java命令，超时时间：%d秒", timeout_seconds)

            env = {**os.environ, **task.to_shard_env()}
            # start_new_session：独立进程组，便于整组暂停/终止（SIGSTOP/killpg）
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,  # 行缓冲
                universal_newlines=True,
                start_new_session=True,
                env=env
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
            
            # 7. 创建日志共享队列
            import queue
            log_queue = queue.Queue()
            
            # 8. 启动日志监控线程（唯一 stdout 消费者）
            log_thread = threading.Thread(
                target=self._monitor_log_output,
                args=(process, log_bucket, log_object, task_run_id, log_queue),
                daemon=True
            )
            log_thread.start()
            
            # 9. 启动状态监控线程（只负责 wait，不再 read stdout）
            status_thread = threading.Thread(
                target=self._monitor_process_status,
                args=(process, task, task_run_id, start_time, log_bucket, log_object, temp_dir, log_queue),
                daemon=True
            )
            status_thread.start()
            
        except Exception as e:
            logger.error("异步任务执行失败（%s）：%s", task.taskId, str(e), exc_info=True)
            self._handle_task_error(task, task_run_id, str(e), start_time)

    def _monitor_log_output(self, process, log_bucket: str, log_object: str, task_run_id: str, log_queue):
        """
        日志监控线程 - 唯一 stdout 消费者
        将日志放入共享队列，由状态线程处理上传
        """
        try:
            logger.info("启动日志监控线程，taskRunId: %s", task_run_id)
            
            import sys
            import time
            from io import BytesIO
            from app.utils.minio_utils import minio_upload_file
            
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
            
            while True:
                line = process.stdout.readline()
                
                # 检查进程是否已退出
                process_exited = process.poll() is not None
                
                # 如果没有输出且进程已退出，退出循环
                if not line:
                    if process_exited:
                        break
                    else:
                        # 进程还没退出，但没有输出（可能缓冲区空），继续等待
                        time.sleep(0.01)
                        continue
                
                if line:
                    # 同时输出到容器stdout（Docker日志中可见）
                    sys.stdout.write(line)
                    sys.stdout.flush()
                    
                    # 写入本地文件
                    with open(local_log_path, 'a', encoding='utf-8') as f:
                        f.write(line)
                    
                    # 放入共享队列
                    log_queue.put(line)
                    
                    # 定时上传日志到MinIO（实时更新日志）
                    current_time = time.time()
                    elapsed = current_time - start_time
                    interval = get_upload_interval(elapsed)
                    
                    if current_time - last_upload_time >= interval:
                        try:
                            with open(local_log_path, 'r', encoding='utf-8') as f:
                                content = f.read()
                            if content:
                                log_buffer = BytesIO(content.encode('utf-8'))
                                minio_upload_file(log_bucket, log_object, log_buffer, len(content))
                                logger.debug("定时日志上传成功，bucket: %s, obj: %s", log_bucket, log_object)
                        except Exception as e:
                            logger.error("定时日志上传失败: %s", str(e))
                        last_upload_time = current_time
                    
                    # 如果进程已退出，发送结束信号
                    if process_exited:
                        log_queue.put(None)  # 发送结束信号
                        break
            
            logger.info("日志监控完成，taskRunId: %s", task_run_id)
            
            # 确保本地文件的最终内容已上传
            import os
            if os.path.exists(local_log_path):
                try:
                    with open(local_log_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    if content:
                        from io import BytesIO
                        from app.utils.minio_utils import minio_upload_file
                        log_buffer = BytesIO(content.encode('utf-8'))
                        minio_upload_file(log_bucket, log_object, log_buffer, len(content))
                        logger.info("最终日志上传成功，bucket: %s, obj: %s", log_bucket, log_object)
                except Exception as e:
                    logger.error("最终日志上传失败: %s", str(e))
                
                # 上传完成后删除本地缓存
                os.remove(local_log_path)
                logger.info("本地日志缓存已删除，taskRunId: %s", task_run_id)
            
            # 上传剩余的日志内容
            if log_content:
                self._upload_partial_log_with_retry(log_bucket, log_object, ''.join(log_content), local_log_path)
                
            logger.info("日志监控完成，taskRunId: %s", task_run_id)
            
            # 删除本地日志缓存前，确保本地文件的最终内容已上传
            import os
            if os.path.exists(local_log_path):
                try:
                    with open(local_log_path, 'r', encoding='utf-8') as f:
                        content = f.read()
                    if content:
                        from io import BytesIO
                        from app.utils.minio_utils import minio_upload_file
                        log_buffer = BytesIO(content.encode('utf-8'))
                        minio_upload_file(log_bucket, log_object, log_buffer, len(content))
                        logger.debug("最终日志上传成功，bucket: %s, obj: %s", log_bucket, log_object)
                except Exception as e:
                    logger.error("最终日志上传失败: %s", str(e))
                
                # 上传完成后删除本地缓存
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
                             temp_dir: str, log_queue):
        """状态监控线程：只 wait 进程，从共享队列收集日志构建 errorMessage"""
        try:
            logger.info("启动状态监控线程，taskRunId: %s", task_run_id)
            
            # 等待进程完成
            exit_code = process.wait()
            
            # 从队列收集所有日志
            all_output_parts = []
            while True:
                try:
                    line = log_queue.get(timeout=1)
                    if line is None:  # 结束信号
                        break
                    all_output_parts.append(line)
                except:
                    break
            
            all_output = ''.join(all_output_parts)
            
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

    def _validate_dependencies(self, task: TaskExecuteMessage, task_run_id: str):
        """验证Worker环境是否满足任务依赖要求，不满足时抛异常"""
        from app.utils.env_utils import EnvUtils
        
        script_tags = task.script.tags if task.script else []
        
        if not script_tags:
            logger.info("任务 %s 无依赖标签要求，跳过依赖验证", task.taskId)
            return
        
        logger.info("开始验证任务 %s 的依赖要求，任务RunId: %s", task.taskId, task_run_id)
        
        # 获取Worker的已安装包列表
        worker_packages = {pkg.lower(): "" for pkg in Config.TAGS}
        
        missing_deps = []
        
        for tag in script_tags:
            if not tag.name:
                continue
                
            pkg_name = tag.name.lower()
            
            if pkg_name not in worker_packages:
                missing_deps.append(tag)
                logger.warning("缺少必需依赖：%s", tag.name)
                continue
        
        error_messages = []
        
        if missing_deps:
            deps_str = ", ".join([t.name for t in missing_deps])
            error_messages.append(f"缺少必需依赖：{deps_str}")
        
        if error_messages:
            error_msg = "; ".join(error_messages)
            logger.error("依赖验证失败：%s", error_msg)
            
            task_instance_manager.update_status(
                task_run_id, 
                "failed",
                error_message=f"依赖验证失败：{error_msg}"
            )
            
            raise Exception(f"依赖验证失败：{error_msg}")
        
        logger.info("任务 %s 依赖验证通过，任务RunId: %s", task.taskId, task_run_id)
    
    def _detect_config_files(self, temp_dir: str) -> list:
        """自动检测临时目录中的 application/bootstrap 配置文件"""
        config_files = []
        
        # 支持的配置文件名
        config_filenames = [
            'application.yml',
            'application.yaml', 
            'application.properties',
            'application-dev.yml',
            'application-dev.yaml',
            'application-dev.properties',
            'application-prod.yml',
            'application-prod.yaml',
            'application-prod.properties'
        ]
        
        try:
            # 遍历目录查找配置文件
            for root, dirs, files in os.walk(temp_dir):
                for filename in files:
                    # 检查是否是配置文件
                    if filename in config_filenames:
                        config_path = os.path.join(root, filename)
                        config_files.append(config_path)
                        logger.info("检测到配置文件：%s", config_path)
                    # 也支持自定义名称的配置文件（如 app.yml, custom.properties）
                    elif filename.endswith('.yml') or filename.endswith('.yaml') or filename.endswith('.properties'):
                        if filename.startswith('application') or filename.startswith('bootstrap'):
                            config_path = os.path.join(root, filename)
                            config_files.append(config_path)
                            logger.info("检测到额外配置文件：%s", config_path)
        except Exception as e:
            logger.warning("检测配置文件时发生异常：%s", str(e))
        
        return config_files
