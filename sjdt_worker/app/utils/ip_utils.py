import socket


class IPUtils:
    @staticmethod
    def get_local_host() -> str:
        """获取本机主机名"""
        return socket.gethostname()

    @staticmethod
    def get_local_ip() -> str:
        """获取本机内网IP"""
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
        finally:
            s.close()
        return ip
