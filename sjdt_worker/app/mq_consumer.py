import time
from rocketmq.client import PushConsumer
from app.config import Config
from app.model.task import TaskExecuteMessage
from app.service.task_service import TaskService
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


class MQConsumer:
    def __init__(self):
        self.consumer = PushConsumer(Config.MQ_CONSUMER_GROUP)
        self.consumer.set_namesrv_addr(Config.MQ_NAMESRV_ADDR)
        self.task_service = TaskService()

        self.consumer.subscribe(
            Config.MQ_TASK_TOPIC,
            self._message_listener,  # callback 是第二个参数
            expression="PUBLIC_TASK || TARGET_TASK"  # expression 作为关键字参数
        )

    def _message_listener(self, msg):
        """消息监听回调函数（返回 True/False 表示消费状态）"""
        if not Config.WORKER_ID:
            logger.warning("未注册Worker，拒绝处理任务")
            return True  # 未注册时直接确认消费，避免重复投递

        try:
            # 解析消息体
            task_body = msg.body.decode("utf-8")
            task = TaskExecuteMessage.from_mq_message(task_body)
            logger.info("收到任务：%s，类型：%s，脚本类型：%s", task.taskId, task.taskType, task.scriptType)

            # 定向任务过滤：仅处理目标为当前Worker的任务
            if task.targetWorkerId and task.targetWorkerId != Config.WORKER_ID:
                logger.debug("任务%s定向到Worker %s，当前Worker为%s，跳过处理", 
                           task.taskId, task.targetWorkerId, Config.WORKER_ID)
                return True  # 跳过但确认消费

            # 处理任务
            logger.info("开始处理任务：%s", task.taskId)
            success = self.task_service.process_task(task)
            if success:
                logger.info("任务处理成功：%s", task.taskId)
            else:
                logger.error("任务处理失败：%s", task.taskId)
            return success  # 任务处理成功返回True，失败返回False

        except Exception as e:
            logger.error("任务处理失败：%s，错误信息：%s", msg.body.decode("utf-8"), str(e), exc_info=True)
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