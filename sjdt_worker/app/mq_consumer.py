import time
from rocketmq.client import PushConsumer, MessageModel
from app.config import Config
from app.model.task import TaskExecuteMessage, TaskStopMessage, TaskPauseMessage, TaskResumeMessage, TaskDeleteMessage
from app.service.task_service import TaskService
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class MQConsumer:
    def __init__(self):
        logger.info("初始化MQ消费者，配置信息：")
        logger.info("- 消费者组(Task)：%s", Config.MQ_CONSUMER_GROUP)
        logger.info("- 消费者组(Control)：%s_CONTROL", Config.MQ_CONSUMER_GROUP)
        logger.info("- NameServer地址：%s", Config.MQ_NAMESRV_ADDR)
        logger.info("- 任务主题：%s", Config.MQ_TASK_TOPIC)
        logger.info("- 控制主题：%s", Config.MQ_TASK_CONTROL_TOPIC)
        logger.info("- 当前Worker ID：%s", Config.WORKER_ID)
        
        self.task_service = TaskService()

        # 1. 任务消费者 (默认集群模式)
        self.task_consumer = PushConsumer(Config.MQ_CONSUMER_GROUP)
        self.task_consumer.set_namesrv_addr(Config.MQ_NAMESRV_ADDR)
        
        logger.info("开始订阅任务执行主题：%s", Config.MQ_TASK_TOPIC)
        self.task_consumer.subscribe(
            Config.MQ_TASK_TOPIC,
            self._message_listener,
            expression="*"
        )
        logger.info("任务执行主题订阅成功")
        
        # 2. 控制消费者 (广播模式)
        # 使用独立的Group以避免冲突，并启用广播模式确保所有Worker都能收到控制指令
        self.control_consumer = PushConsumer(Config.MQ_CONSUMER_GROUP + "_CONTROL")
        self.control_consumer.set_namesrv_addr(Config.MQ_NAMESRV_ADDR)
        self.control_consumer.set_message_model(MessageModel.BROADCASTING)
        
        logger.info("开始订阅任务控制主题(广播模式)：%s", Config.MQ_TASK_CONTROL_TOPIC)
        self.control_consumer.subscribe(
            Config.MQ_TASK_CONTROL_TOPIC,
            self._control_task_listener,
            expression="*"
        )
        logger.info("任务控制主题订阅成功")

    def _message_listener(self, msg):
        """消息监听回调函数（返回 True/False 表示消费状态）"""
        logger.info("开始处理MQ消息...")
        
        try:
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
            
            logger.info("收到MQ消息 - MsgID: %s, Topic: %s, Tag: %s, Offset: %s", 
                       msg_msg_id, msg_topic, msg_tags, msg_offset)
            
            if not Config.WORKER_ID:
                logger.warning("Worker未注册，拒绝处理任务")
                return True
            
            try:
                msg_body = msg.body.decode("utf-8")
                logger.debug("消息内容：%s", msg_body[:200] + "..." if len(msg_body) > 200 else msg_body)
                
                task = TaskExecuteMessage.from_mq_message(msg_body)
                
                logger.info("消息解析成功 - TaskID: %s, TaskRunID: %s, TaskType: %s, ScriptType: %s", 
                           task.taskId, task.taskRunId, task.taskType, task.scriptType)
                
                logger.info("开始处理任务：%s，目标Worker: %s，当前Worker: %s", 
                           task.taskId, task.targetWorkerId, Config.WORKER_ID)

                if task.targetWorkerId:
                    if task.targetWorkerId == Config.WORKER_ID:
                        logger.info("定向任务匹配，任务%s定向到当前Worker %s，开始处理", 
                                   task.taskId, Config.WORKER_ID)
                    else:
                        logger.info("定向任务不匹配，任务%s定向到Worker %s，当前Worker为%s，跳过处理", 
                                   task.taskId, task.targetWorkerId, Config.WORKER_ID)
                        return True
                else:
                    logger.info("公共任务，任务%s为公共任务，当前Worker %s开始处理", 
                               task.taskId, Config.WORKER_ID)

                logger.info("执行任务：%s", task.taskId)
                success = self.task_service.process_task(task)
                if success:
                    logger.info("任务处理成功：%s", task.taskId)
                else:
                    logger.error("任务处理失败：%s", task.taskId)
                return success
            except Exception as parse_error:
                logger.error("消息解析失败：%s", str(parse_error), exc_info=True)
                return False

        except Exception as e:
            logger.error("消息处理异常：%s", str(e), exc_info=True)
            try:
                msg_body_str = msg.body.decode("utf-8") if msg.body else "空消息体"
                logger.error("异常消息内容：%s", msg_body_str)
            except Exception:
                logger.error("无法解析消息体")
            return False

    def _control_task_listener(self, msg):
        """统一的任务控制消息监听回调函数"""
        if not Config.WORKER_ID:
            logger.warning("未注册Worker，拒绝处理任务控制请求")
            return True

        try:
            msg_topic = getattr(msg, 'topic', 'UNKNOWN')
            msg_tags = getattr(msg, 'tags', 'UNKNOWN')
            
            logger.info("收到控制消息 - Topic: %s, Tag: %s", msg_topic, msg_tags)
            
            if msg_topic == Config.MQ_TASK_TOPIC:
                logger.info("转发任务执行消息到_message_listener处理")
                return self._message_listener(msg)
            
            control_body = msg.body.decode("utf-8")
            
            msg_tag = msg.tags
            if isinstance(msg_tag, bytes):
                msg_tag = msg_tag.decode('utf-8')
            logger.debug("收到任务控制消息，Tag: %s", msg_tag)
            
            control_message = None
            if msg_tag in ["STOP_TASK", "KILL_TASK"]:
                control_message = TaskStopMessage.from_mq_message(control_body)
            elif msg_tag in ["PAUSE_TASK"]:
                control_message = TaskPauseMessage.from_mq_message(control_body)
            elif msg_tag in ["RESUME_TASK"]:
                control_message = TaskResumeMessage.from_mq_message(control_body)
            elif msg_tag in ["DELETE_TASK"]:
                control_message = TaskDeleteMessage.from_mq_message(control_body)
            else:
                logger.error("未知的任务控制类型：%s", msg_tag)
                return True
            
            if control_message.targetWorkerId and control_message.targetWorkerId != Config.WORKER_ID:
                logger.debug("任务控制请求定向到Worker %s，当前Worker为%s，跳过处理", 
                           control_message.targetWorkerId, Config.WORKER_ID)
                return True
            
            logger.info("收到任务控制请求：taskId=%s，taskRunId=%s，原因=%s，类型=%s", 
                      control_message.taskId, control_message.taskRunId, 
                      getattr(control_message, 'reason', None), msg_tag)
            
            success = False
            if msg_tag == "STOP_TASK":
                logger.info("处理停止任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.stop_task(control_message)
                
            elif msg_tag == "PAUSE_TASK":
                logger.info("处理暂停任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.pause_task(control_message)
                
            elif msg_tag == "RESUME_TASK":
                logger.info("处理恢复任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.resume_task(control_message)
                
            elif msg_tag == "KILL_TASK":
                logger.info("处理强制终止任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.kill_task(control_message)
                
            elif msg_tag == "DELETE_TASK":
                logger.info("处理删除任务请求：taskRunId=%s", control_message.taskRunId)
                success = self.task_service.delete_task(control_message)
            
            if success:
                logger.info("任务控制操作成功，Tag: %s，taskRunId: %s", msg_tag, control_message.taskRunId)
            else:
                logger.error("任务控制操作失败，Tag: %s，taskRunId: %s", msg_tag, control_message.taskRunId)
            return success

        except Exception as e:
            logger.error("任务控制处理失败：%s，错误信息：%s", msg.body.decode("utf-8"), str(e), exc_info=True)
            return False

    def start(self):
        """启动消费者"""
        try:
            logger.info("启动RocketMQ消费者(Task & Control)...")
            self.task_consumer.start()
            self.control_consumer.start()
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
        try:
            self.task_consumer.shutdown()
        except Exception as e:
            logger.error("关闭任务消费者失败: %s", str(e))
            
        try:
            self.control_consumer.shutdown()
        except Exception as e:
            logger.error("关闭控制消费者失败: %s", str(e))
