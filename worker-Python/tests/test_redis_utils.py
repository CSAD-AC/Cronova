"""Redis 执行权抢占（步骤27）单元测试。

不依赖真实 Redis：以内存实现的假客户端替换 get_redis_client，验证 SETNX 幂等语义：
同一 runId 首次抢占成功、重复抢占失败；不同 runId 互不影响。
"""
import unittest

import app.utils.redis_utils as redis_utils
from app.utils.redis_utils import acquire_run_claim


class FakeRedis:
    """实现 claim 所需子集（SET NX EX）的内存假客户端"""

    def __init__(self):
        self._data = {}

    def set(self, name, value, nx=False, ex=None):
        if nx and name in self._data:
            return None
        self._data[name] = value
        return True

    def delete(self, *names):
        removed = 0
        for n in names:
            if n in self._data:
                del self._data[n]
                removed += 1
        return removed


class RedisClaimTest(unittest.TestCase):
    """SETNX 执行权抢占（幂等执行）测试"""

    def setUp(self):
        self.fake = FakeRedis()
        # 替换客户端工厂，避免依赖真实 Redis 模块
        redis_utils.get_redis_client = lambda: self.fake
        self.run_id = "ut_claim_1"

    def test_first_wins_second_skipped(self):
        """同一 runId：首次抢占成功，重复抢占失败（幂等）"""
        first = acquire_run_claim(self.run_id, "worker-a", 60)
        second = acquire_run_claim(self.run_id, "worker-b", 60)
        self.assertTrue(first, "首次抢占应成功")
        self.assertFalse(second, "重复消息的抢占应失败")

    def test_distinct_run_ids_independent(self):
        """不同 runId 互不影响"""
        run_b = "ut_claim_2"
        try:
            self.assertTrue(acquire_run_claim(self.run_id, "worker-a", 60))
            self.assertTrue(acquire_run_claim(run_b, "worker-a", 60), "不同runId应可独立抢占")
        finally:
            self.fake.delete(f"task:run:claim:{run_b}")

    def test_empty_run_id_allows_execution(self):
        """runId 为空时不抢占，直接放行"""
        self.assertTrue(acquire_run_claim(None, "worker-a", 60))


if __name__ == "__main__":
    unittest.main()