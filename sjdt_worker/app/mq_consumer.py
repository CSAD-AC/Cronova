import time
from rocketmq.client import PushConsumer
from app.config import Config
from app.model.task import TaskExecuteMessage
from app.service.task_service import TaskService


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
            print("未注册Worker，拒绝处理任务")
            return True  # 未注册时直接确认消费，避免重复投递

        try:
            # 解析消息体
            task_body = msg.body.decode("utf-8")
            task = TaskExecuteMessage.from_mq_message(task_body)
            print(f"收到任务：{task.taskId}，类型：{task.taskType}")

            # 定向任务过滤：仅处理目标为当前Worker的任务
            if task.targetWorkerId and task.targetWorkerId != Config.WORKER_ID:
                print(f"任务{task.taskId}定向到Worker {task.targetWorkerId}，当前Worker为{Config.WORKER_ID}，跳过处理")
                return True  # 跳过但确认消费

            # 处理任务
            success = self.task_service.process_task(task)
            return success  # 任务处理成功返回True，失败返回False

        except Exception as e:
            print(f"任务处理失败：{str(e)}")
            return False  # 异常时返回False，触发重试

    def start(self):
        """启动消费者"""
        try:
            print("启动RocketMQ消费者...")
            self.consumer.start()
            # 阻塞主线程
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("收到中断信号，准备关闭...")
        except Exception as e:
            print(f"MQ消费者运行异常：{str(e)}")
        finally:
            self.shutdown()

    def shutdown(self):
        """关闭消费者"""
        print("关闭RocketMQ消费者...")
        self.consumer.shutdown()