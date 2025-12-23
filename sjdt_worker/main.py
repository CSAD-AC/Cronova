import threading
from apscheduler.schedulers.background import BackgroundScheduler
from app.config import Config
from app.mq_consumer import MQConsumer
from app.service.auth_service import AuthService
from app.service.register_service import RegisterService
from app.service.report_service import ReportService


def main():
    # 1. 加载配置并验证有效性
    config_loaded = Config.load_local_config()

    # 2. 验证AK/SK有效性（如果配置已加载）
    if config_loaded:
        if not AuthService.validate_aksk():
            print("AK/SK验证失败，尝试重新注册...")
            config_loaded = False  # 标记为需要重新注册

    # 3. 若配置无效或未加载，执行注册流程
    if not config_loaded:
        print("开始Worker注册流程...")
        if not RegisterService.register_worker():
            print("Worker注册失败，程序退出")
            return

    # 4. 启动MQ消费者
    mq_consumer = MQConsumer()
    mq_thread = threading.Thread(target=mq_consumer.start, daemon=True)
    mq_thread.start()

    # 5. 启动定时任务
    scheduler = BackgroundScheduler()
    scheduler.add_job(ReportService.send_heartbeat, "interval", seconds=Config.HEARTBEAT_INTERVAL)
    scheduler.add_job(ReportService.report_status, "interval", seconds=Config.STATUS_REPORT_INTERVAL)
    scheduler.start()
    print("程序启动成功，开始运行...")

    # 保持主线程
    try:
        while True:
            threading.Event().wait()
    except KeyboardInterrupt:
        scheduler.shutdown()
        mq_consumer.shutdown()
        print("程序正常退出")


if __name__ == "__main__":
    main()
