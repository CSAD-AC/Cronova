import time
import threading
from app.config import Config
from app.service.register_service import RegisterService
from app.service.report_service import ReportService
from app.service.auth_service import AuthService


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

    # 4. 独立的心跳线程
    def heartbeat_task():
        pass
        while True:
            heartbeat_success = ReportService.send_heartbeat()
            if not heartbeat_success:
                print("心跳发送失败，检查服务连接或AK/SK有效性")
            else:
                print("心跳发送成功")
            time.sleep(Config.HEARTBEAT_INTERVAL)  # 使用配置的心跳间隔（2秒）

    # 5. 独立的状态上报线程
    def status_report_task():
        while True:
            status_success = ReportService.report_status()
            if not status_success:
                print("状态上报失败，检查服务连接或AK/SK有效性")
            time.sleep(Config.STATUS_REPORT_INTERVAL)  # 使用配置的状态上报间隔（10秒）

    # 启动两个独立线程
    heartbeat_thread = threading.Thread(target=heartbeat_task, daemon=True)
    status_thread = threading.Thread(target=status_report_task, daemon=True)

    heartbeat_thread.start()
    status_thread.start()

    print(f"系统监控线程已启动（心跳间隔{Config.HEARTBEAT_INTERVAL}秒，状态上报间隔{Config.STATUS_REPORT_INTERVAL}秒）")

    # 保持主程序运行
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("程序手动终止，开始资源清理...")
        print("资源清理完成，退出程序")


if __name__ == "__main__":
    main()