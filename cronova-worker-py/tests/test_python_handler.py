import signal
import sys
import types
import unittest
from unittest.mock import MagicMock, patch

rocketmq = types.ModuleType("rocketmq")
rocketmq_client = types.ModuleType("rocketmq.client")
rocketmq_client.Producer = MagicMock
rocketmq_client.Message = MagicMock
sys.modules.setdefault("rocketmq", rocketmq)
sys.modules.setdefault("rocketmq.client", rocketmq_client)

from app.service.task_handler.python_handler import KILL_SIGNAL, PythonHandler


class PythonHandlerIsolationTest(unittest.TestCase):
    def test_run_root_is_unique_per_task_run(self):
        root_one = PythonHandler._run_root("/app/app/scripts_tmp", "run-1")
        root_two = PythonHandler._run_root("/app/app/scripts_tmp", "run-2")

        self.assertNotEqual(root_one, root_two)
        self.assertTrue(root_one.endswith("run-1"))
        self.assertTrue(root_two.endswith("run-2"))

    def test_terminate_process_group_uses_killpg(self):
        process = MagicMock()
        process.pid = 123

        with patch(
            "app.service.task_handler.python_handler.os.getpgid",
            return_value=456,
            create=True,
        ), patch(
            "app.service.task_handler.python_handler.os.killpg",
            create=True,
        ) as killpg:
            PythonHandler._terminate_process_group(process)

        killpg.assert_called_once_with(456, KILL_SIGNAL)


if __name__ == "__main__":
    unittest.main()
