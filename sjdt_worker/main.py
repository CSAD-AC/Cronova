import threading
from apscheduler.schedulers.background import BackgroundScheduler
from app.config import Config
from app.mq_consumer import MQConsumer
from app.service.auth_service import AuthService
from app.service.register_service import RegisterService
from app.service.report_service import ReportService
from app.service.task_status_service import task_status_service
from app.utils.log_utils import get_logger

# 初始化日志器
logger = get_logger(__name__)


def main():
    # 1. 加载配置并验证有效性
    logger.info("开始加载配置...")
    config_loaded = Config.load_local_config()

    # 2. 验证AK/SK有效性（如果配置已加载）
    if config_loaded:
        logger.info("配置加载成功，开始验证AK/SK有效性...")
        if not AuthService.validate_aksk():
            logger.warning("AK/SK验证失败，尝试重新注册...")
            config_loaded = False  # 标记为需要重新注册

    # 3. 若配置无效或未加载，执行注册流程
    if not config_loaded:
        logger.info("开始Worker注册流程...")
        if not RegisterService.register_worker():
            logger.error("Worker注册失败，程序退出")
            return
        logger.info("Worker注册成功")

    # 4. 启动MQ消费者
    logger.info("启动MQ消费者...")
    mq_consumer = MQConsumer()
    mq_thread = threading.Thread(target=mq_consumer.start, daemon=True)
    mq_thread.start()
    logger.info("MQ消费者启动成功")

    # 5. 启动定时任务
    logger.info("启动定时任务...")
    scheduler = BackgroundScheduler()
    scheduler.add_job(ReportService.send_heartbeat, "interval", seconds=Config.HEARTBEAT_INTERVAL)
    scheduler.add_job(ReportService.report_status, "interval", seconds=Config.STATUS_REPORT_INTERVAL)
    scheduler.add_job(task_status_service.report_task_status, "interval", seconds=5)  # 每5秒报告一次任务状态
    scheduler.start()
    logger.info("程序启动成功，开始运行...")

    # 保持主线程
    try:
        while True:
            threading.Event().wait()
    except KeyboardInterrupt:
        logger.info("收到中断信号，开始清理任务...")
        
        # 1. 关闭定时任务调度器
        scheduler.shutdown()
        
        # 2. 清理所有运行中的任务
        from app.service.process_manager import process_manager
        from app.service.task_service import TaskService
        from app.model.task import TaskStopMessage
        
        # 获取所有运行中的进程
        running_processes = process_manager.get_all_processes()
        if running_processes:
            logger.info("开始终止 %d 个运行中的任务...", len(running_processes))
            
            task_service = TaskService()
            for task_run_id, process_info in running_processes.items():
                task = process_info.get('task')
                if task:
                    logger.info("终止任务：%s，taskRunId：%s", task.taskId, task_run_id)
                    
                    # 创建停止消息
                    stop_message = TaskStopMessage(
                        taskId=task.taskId,
                        taskRunId=task_run_id,
                        targetWorkerId=Config.WORKER_ID,
                        reason="执行器收到中断信号，任务被终止"
                    )
                    
                    # 停止任务
                    task_service.stop_task(stop_message)
            
            logger.info("所有任务已终止")
        
        # 3. 关闭MQ消费者
        mq_consumer.shutdown()
        
        logger.info("程序正常退出")


if __name__ == "__main__":
    main()
