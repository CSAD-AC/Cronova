"""Redis 连接工具。

供 Worker 幂等执行权抢占用（SETNX claim）。

Redis 地址对齐调度器配置，从环境变量 / .env 读取同一组键名：
REDIS_HOST / REDIS_PORT / REDIS_DATABASE / REDIS_PASSWORD（真实地址经 .env 注入，不入库 git）。
"""
import os

try:
    import redis as redis_client
except ImportError:  # redis 未安装时降级：claim 判定退化为"允许执行"
    redis_client = None

# 连接参数（与调度器同一键名，默认本地开发值）
_REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
_REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
_REDIS_DATABASE = int(os.getenv("REDIS_DATABASE", "1"))
_REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "") or None

_client = None


def get_redis_client():
    """获取全局 Redis 客户端（懒加载单例）。

    :return: redis.Redis 实例；Redis 客户端不可用时返回 None
    """
    global _client
    if redis_client is None:
        return None
    if _client is None:
        _client = redis_client.Redis(
            host=_REDIS_HOST,
            port=_REDIS_PORT,
            db=_REDIS_DATABASE,
            password=_REDIS_PASSWORD,
            socket_connect_timeout=2,
            socket_timeout=2,
            decode_responses=True,
        )
    return _client


def acquire_run_claim(task_run_id, worker_id, ttl_seconds):
    """以 SETNX 抢占某个 run 的执行权（幂等执行兜底）。

    MQ 是 at-least-once，可能重投；同一 run 只允许首个 Worker 真正执行一次，
    后续重复消息因抢占失败直接 ACK 但不重复跑。

    :param task_run_id: 任务运行实例ID
    :param worker_id: 当前 WorkerID
    :param ttl_seconds: 锁TTL（秒），通常取任务超时 + 60，覆盖整个执行窗口
    :return: True=抢占成功可执行；False=已被其他消息抢占，应跳过
    """
    if not task_run_id:
        return True
    client = get_redis_client()
    if client is None:
        # Redis 不可用：fail-open，不阻塞任务执行（丢失幂等但保证可用性）
        return True
    try:
        claimed = client.set(f"task:run:claim:{task_run_id}", worker_id,
                             nx=True, ex=int(ttl_seconds or 60))
        return bool(claimed)
    except Exception:
        # 网络抖动等：fail-open，允许执行，避免把任务搞丢
        return True