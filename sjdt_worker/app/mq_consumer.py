import time
from rocketmq.client import PushConsumer
from app.config import Config
from app.model.task import TaskExecuteMessage, TaskStopMessage, TaskPauseMessage, TaskResumeMessage, TaskDeleteMessage
from app.service.task_service import TaskService
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class MQConsumer:
    def __init__(self):
        # 添加MQ配置调试日志
        logger.info("📋 初始化MQ消费者，配置信息：")
        logger.info("📋 - 消费者组：%s", Config.MQ_CONSUMER_GROUP)
        logger.info("📋 - NameServer地址：%s", Config.MQ_NAMESRV_ADDR)
        logger.info("📋 - 任务主题：%s", Config.MQ_TASK_TOPIC)
        logger.info("📋 - 控制主题：%s", Config.MQ_TASK_CONTROL_TOPIC)
        logger.info("📋 - 当前Worker ID：%s", Config.WORKER_ID)
        
        self.consumer = PushConsumer(Config.MQ_CONSUMER_GROUP)
        self.consumer.set_namesrv_addr(Config.MQ_NAMESRV_ADDR)
        self.task_service = TaskService()

        # 订阅任务执行主题
        logger.info("📋 开始订阅任务执行主题：%s", Config.MQ_TASK_TOPIC)
        self.consumer.subscribe(
            Config.MQ_TASK_TOPIC,
            self._message_listener,
            expression="*"
        )
        logger.info("📋 任务执行主题订阅成功")
        
        # 订阅任务控制主题
        logger.info("📋 开始订阅任务控制主题：%s", Config.MQ_TASK_CONTROL_TOPIC)
        self.consumer.subscribe(
            Config.MQ_TASK_CONTROL_TOPIC,
            self._control_task_listener,
            expression="*"
        )
        logger.info("📋 任务控制主题订阅成功")

    def _message_listener(self, msg):
        """消息监听回调函数（返回 True/False 表示消费状态）"""
        logger.info("🔍 _message_listener被调用，开始处理消息...")
        
        try:
            # 获取消息详情用于调试
            msg_topic = getattr(msg, 'topic', 'UNKNOWN')
            msg_tags = getattr(msg, 'tags', 'UNKNOWN')
            msg_msg_id = getattr(msg, 'msg_id', 'UNKNOWN')
            msg_offset = getattr(msg, 'queue_offset', 'UNKNOWN')
            
            if isinstance(msg_topic, bytes):
                msg_topic = msg_topic.decode('utf-8')
            if isinstance(msg_tags, bytes):
                msg_tags = msg_tags.decode('utf-8')
            if isinstance(msg_msg_id, bytes):
                msg_msg_id = msg_msg_id.decode('utf-8')
            
            # 添加详细的消息接收日志（INFO级别，确保能看到）
            logger.info("📥 【消息接收】收到MQ消息 - MsgID: %s, Topic: %s, Tag: %s, Offset: %s", 
                       msg_msg_id, msg_topic, msg_tags, msg_offset)
            
            # 检查Worker ID
            if not Config.WORKER_ID:
                logger.warning("⚠️ 【Worker未注册】拒绝处理任务，直接确认消费")
                return True  # 未注册时直接确认消费，避免重复投递
            
            # 尝试解析消息体
            try:
                msg_body = msg.body.decode("utf-8")
                logger.info("📋 【消息内容】%s", msg_body[:200] + "..." if len(msg_body) > 200 else msg_body)
                
                # 解析消息体
                task = TaskExecuteMessage.from_mq_message(msg_body)
                
                logger.info("✅ 【消息解析成功】TaskID: %s, TaskRunID: %s, TaskType: %s, ScriptType: %s", 
                           task.taskId, task.taskRunId, task.taskType, task.scriptType)
                
                logger.info("🚀 【开始处理】任务：%s，目标Worker: %s，当前Worker: %s", 
                           task.taskId, task.targetWorkerId, Config.WORKER_ID)

                # 检查是否是定向任务，且目标不是当前Worker
                if task.targetWorkerId:
                    if task.targetWorkerId == Config.WORKER_ID:
                        logger.info("🎯 【定向任务匹配】任务%s定向到当前Worker %s，开始处理", 
                                   task.taskId, Config.WORKER_ID)
                    else:
                        logger.info("🔄 【定向任务不匹配】任务%s定向到Worker %s，当前Worker为%s，跳过处理", 
                                   task.taskId, task.targetWorkerId, Config.WORKER_ID)
                        return True  # 跳过但确认消费
                else:
                    logger.info("🌍 【公共任务】任务%s为公共任务，当前Worker %s开始处理", 
                               task.taskId, Config.WORKER_ID)

                # 处理任务
                logger.info("🔧 【执行任务】开始处理任务：%s", task.taskId)
                success = self.task_service.process_task(task)
                if success:
                    logger.info("✅ 【任务完成】任务处理成功：%s", task.taskId)
                else:
                    logger.error("❌ 【任务失败】任务处理失败：%s", task.taskId)
                return success  # 任务处理成功返回True，失败返回False
            except Exception as parse_error:
                logger.error("❌ 【消息解析失败】解析消息体时出错：%s", str(parse_error), exc_info=True)
                logger.error("❌ 【消息解析失败详情】消息体：%s", msg_body)
                return False  # 解析失败时返回False，触发重试

        except Exception as e:
            logger.error("💥 【消息处理异常】_message_listener任务处理失败，错误信息：%s", str(e), exc_info=True)
            # 尝试获取消息体用于调试
            try:
                msg_body_str = msg.body.decode("utf-8") if msg.body else "空消息体"
                logger.error("💥 【异常消息内容】%s", msg_body_str)
            except Exception:
                logger.error("💥 【无法解析消息体】")
            return False  # 异常时返回False，触发重试

    def _control_task_listener(self, msg):
        """统一的任务控制消息监听回调函数（返回 True/False 表示消费状态）"""
        if not Config.WORKER_ID:
            logger.warning("未注册Worker，拒绝处理任务控制请求")
            return True  # 未注册时直接确认消费，避免重复投递

        try:
            # 获取消息Topic和Tag
            msg_topic = getattr(msg, 'topic', 'UNKNOWN')
            msg_tags = getattr(msg, 'tags', 'UNKNOWN')
            
            # 添加详细的消息路由调试日志
            logger.info("📥🔧 _control_task_listener收到消息 - Topic: %s, Tag: %s", msg_topic, msg_tags)
            logger.info("📥🔧 消息内容: %s", msg.body.decode("utf-8")[:200] + "..." if len(msg.body.decode("utf-8")) > 200 else msg.body.decode("utf-8"))
            
            # 如果是任务执行主题的消息，转发给消息监听器处理
            if msg_topic == Config.MQ_TASK_TOPIC:
                logger.info("发现任务执行主题消息，转发给_message_listener处理")
                return self._message_listener(msg)
            
            # 解析消息体
            control_body = msg.body.decode("utf-8")
            
            # 根据Tag确定操作类型
            msg_tag = msg.tags
            if isinstance(msg_tag, bytes):
                msg_tag = msg_tag.decode('utf-8')
            logger.debug("收到任务控制消息，Tag: %s", msg_tag)
            
            # 解析消息并获取控制信息
            control_message = None
            if msg_tag in ["STOP_TASK"]:
                control_message = TaskStopMessage.from_mq_message(control_body)
            elif msg_tag in ["PAUSE_TASK"]:
                control_message = TaskPauseMessage.from_mq_message(control_body)
            elif msg_tag in ["RESUME_TASK"]:
                control_message = TaskResumeMessage.from_mq_message(control_body)
            elif msg_tag in ["DELETE_TASK"]:
                control_message = TaskDeleteMessage.from_mq_message(control_body)
            else:
                logger.error("未知的任务控制类型：%s", msg_tag)
                return True  # 未知类型直接确认消费
            
            # 统一定向任务过滤：仅处理目标为当前Worker的任务
            if control_message.targetWorkerId and control_message.targetWorkerId != Config.WORKER_ID:
                logger.debug("任务控制请求定向到Worker %s，当前Worker为%s，跳过处理", 
                           control_message.targetWorkerId, Config.WORKER_ID)
                return True  # 跳过但确认消费
            
            # 记录控制信息（适用于所有操作类型）
            logger.info("收到任务控制请求：taskId=%s，taskRunId=%s，原因=%s，类型=%s", 
                      control_message.taskId, control_message.taskRunId, 
                      getattr(control_message, 'reason', None), msg_tag)
            
            # 根据Tag分发到具体处理逻辑
            success = False
            if msg_tag == "STOP_TASK":
                logger.info("开始处理停止任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.stop_task(control_message)
                
            elif msg_tag == "PAUSE_TASK":
                logger.info("开始处理暂停任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.pause_task(control_message)
                
            elif msg_tag == "RESUME_TASK":
                logger.info("开始处理恢复任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.resume_task(control_message)
                
            elif msg_tag == "DELETE_TASK":
                logger.info("开始处理删除任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.delete_task(control_message)
            
            # 处理结果
            if success:
                logger.info("任务控制操作成功，Tag: %s，taskRunId: %s", msg_tag, control_message.taskRunId)
            else:
                logger.error("任务控制操作失败，Tag: %s，taskRunId: %s", msg_tag, control_message.taskRunId)
            return success  # 任务处理成功返回True，失败返回False

        except Exception as e:
            logger.error("任务控制处理失败：%s，错误信息：%s", msg.body.decode("utf-8"), str(e), exc_info=True)
            return False  # 异常时返回False，触发重试

    def start(self):
        """启动消费者"""
        try:
            logger.info("启动RocketMQ消费者...")
            self.consumer.start()
            # 阻塞主线程
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("收到中断信号，准备关闭...")
        except Exception as e:
            logger.error("MQ消费者运行异常：%s", str(e), exc_info=True)
        finally:
            self.shutdown()

    def shutdown(self):
        """关闭消费者"""
        logger.info("关闭RocketMQ消费者...")
        self.consumer.shutdown()