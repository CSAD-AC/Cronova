"""任务日志管道：本地落盘 + 增量追加 MinIO + 尾部环形缓冲 + CFL 标准化（可选）。

设计要点：
1. 传输量 O(N)：只上传 [uploaded_offset, 文件末尾) 的增量字节（tail + Compose）；
2. 内存有界：只保留尾部 tail_lines 行（环形缓冲）；
3. 标准格式化（normalize=True）：逐行归一化为 CFL（Canonical Log Line，见 cfl.py），
   日志含 UTC 毫秒时间戳/8 宽级别/可选 shard 字段，分片聚合可按时间稳定排序；
4. 条目索引 sidecar（.idx）：头行落盘时同步追加一条 JSONL 记录
   {"t":毫秒,"o":字节偏移,"l":级别缩写}，增量上传。

使用方（handler）：
    pipeline = TaskLogPipeline(log_bucket, log_object, local_log_path,
                               normalize=True, shard_index=3, shard_total=8)
    pipeline.stream(process)          # 唯一 stdout 消费循环（含周期 flush 与收尾）
    pipeline.error_tail(1000)         # 任务失败时取尾部文本作为 errorMessage
"""
import json
import os
import sys
import time
from collections import deque
from typing import Optional

from app.service.cfl import CflFormatter
from app.utils.log_utils import get_logger
from app.utils.minio_utils import append_log, get_minio_client

logger = get_logger(__name__)


def build_log_object(task, task_run_id: str) -> str:
    """构造任务日志对象名（含分片维度，方案 4.5）。

    - 分片任务：`task-logs/{taskId}/{shardBatchId}/{shardIndex}-{runId}.log`
    - 非分片任务：`task-logs/{taskId}/{runId}.log`
    """
    task_id = getattr(task, "taskId", None) or "unknown"
    shard_total = getattr(task, "shardTotal", None)
    if shard_total and shard_total > 1:
        shard_batch = getattr(task, "shardBatchId", None) or "batch"
        shard_index = getattr(task, "shardIndex", None)
        return f"task-logs/{task_id}/{shard_batch}/{shard_index}-{task_run_id}.log"
    return f"task-logs/{task_id}/{task_run_id}.log"


class TaskLogPipeline:
    """任务日志管道（进程内每任务一个实例，单写入线程）"""

    def __init__(self, log_bucket: str, log_object: str, local_log_path: str,
                 tail_lines: int = 1000, mirror_stdout: bool = True,
                 normalize: bool = False,
                 shard_index: Optional[int] = None,
                 shard_total: Optional[int] = None):
        self.log_bucket = log_bucket
        self.log_object = log_object
        self.local_log_path = local_log_path
        self.local_idx_path = local_log_path + ".idx"
        self.tail_lines = tail_lines
        self.mirror_stdout = mirror_stdout
        self.normalize = normalize
        self.shard_index = shard_index
        self.shard_total = shard_total

        # 尾部环形缓冲：只保留最近 tail_lines 行，避免全量缓存
        self._buffer = deque(maxlen=tail_lines)
        # 已上传到 MinIO 的本地文件字节数（log 与 idx 各自维护，均可崩溃恢复）
        self._uploaded_offset = 0
        self._idx_uploaded_offset = 0
        self._closed = False

        # CFL 归一化器（可选）
        self._cfl = (CflFormatter(shard_index, shard_total) if normalize else None)

        # 崩溃恢复：远端对象大小 = 已上传字节数（本地文件是其前缀）
        try:
            size = get_minio_client().stat_object(self.log_bucket, self.log_object).size
            self._uploaded_offset = size if size > 0 else 0
        except Exception:
            self._uploaded_offset = 0
        if self._cfl is not None:
            try:
                size = get_minio_client().stat_object(
                    self.log_bucket, self.log_object + ".idx").size
                self._idx_uploaded_offset = size if size > 0 else 0
            except Exception:
                self._idx_uploaded_offset = 0

    # ------------------------------------------------------------------ 写入

    def write(self, line: str) -> int:
        """写入一行日志：本地文件（字节，逐次打开-写入-关闭）+ 环形缓冲；可选镜像到容器 stdout。

        normalize=True 时逐行归一化为 CFL：头行重写、续行原样落盘，并同步产出 .idx 记录。
        返回写入的字节数。
        """
        os.makedirs(os.path.dirname(self.local_log_path) or ".", exist_ok=True)
        receive_ts = int(time.time() * 1000)

        if self._cfl is not None:
            entry = self._cfl.process(line, receive_ts)
            if entry is not None:
                # 新条目：CFL 头行落盘 + idx 记录
                header = self._cfl.format_header(entry)
                data = (header + "\n").encode("utf-8")
                offset_before = (os.path.getsize(self.local_log_path)
                                 if os.path.exists(self.local_log_path) else 0)
                self._append_bytes(self.local_log_path, data)
                self._append_idx({
                    "t": entry.ts_millis,
                    "o": offset_before,
                    "l": entry.level_abbr(),
                })
                display = header + "\n"
                bytes_written = len(data)
            else:
                # 续行：原样落盘（不单独产生 idx 记录）
                data = line.encode("utf-8")
                self._append_bytes(self.local_log_path, data)
                display = line
                bytes_written = len(data)
        else:
            data = line.encode("utf-8")
            self._append_bytes(self.local_log_path, data)
            display = line
            bytes_written = len(data)

        self._buffer.append(display)
        if self.mirror_stdout:
            sys.stdout.write(display)
            sys.stdout.flush()
        return bytes_written

    def _append_bytes(self, path: str, data: bytes) -> None:
        with open(path, "ab", buffering=0) as f:
            f.write(data)

    def _append_idx(self, record: dict) -> None:
        self._append_bytes(self.local_idx_path,
                           (json.dumps(record, ensure_ascii=False) + "\n").encode("utf-8"))

    # ------------------------------------------------------------------ 上传

    def flush(self) -> int:
        """上传 [uploaded_offset, 文件末尾) 的增量字节（log + idx），返回新 log 偏移。

        上传失败向上抛出，由调用方决定是否重试；本地文件始终保留直至 close 成功。
        """
        if self._closed:
            return self._uploaded_offset
        self._uploaded_offset = self._flush_one(
            self.log_bucket, self.log_object, self.local_log_path, self._uploaded_offset)
        if self._cfl is not None:
            self._idx_uploaded_offset = self._flush_one(
                self.log_bucket, self.log_object + ".idx", self.local_idx_path,
                self._idx_uploaded_offset)
        return self._uploaded_offset

    @staticmethod
    def _flush_one(bucket: str, obj: str, path: str, uploaded: int) -> int:
        """上传单个本地文件的增量字节，返回新偏移。"""
        size = os.path.getsize(path) if os.path.exists(path) else uploaded
        if size <= uploaded:
            return uploaded
        with open(path, "rb") as f:
            f.seek(uploaded)
            data = f.read()
        if data:
            return append_log(bucket, obj, data, uploaded)
        return uploaded

    def close(self) -> bool:
        """最终 flush 并删除本地文件（log + idx）；成功返回 True，失败返回 False（本地保留）。

        close 后不再接受写入。
        """
        if self._closed:
            return True
        try:
            self.flush()
            for path in (self.local_log_path, self.local_idx_path):
                if os.path.exists(path):
                    os.remove(path)
            self._closed = True
            return True
        except Exception as e:
            logger.error("日志最终 flush 失败，本地文件保留: %s，错误: %s", self.local_log_path, str(e))
            return False

    # ------------------------------------------------------------------ 消费

    def stream(self, process, flush_interval: float = 5.0) -> bool:
        """唯一 stdout 消费循环：读进程行 → write → 周期 flush，直到进程退出。

        进程退出后执行最终 flush 并返回是否成功。
        """
        last_flush = time.monotonic()
        while True:
            line = process.stdout.readline()
            process_exited = process.poll() is not None
            if not line:
                if process_exited:
                    break
                # 进程未退出但暂时无输出：避免忙轮询
                time.sleep(0.01)
                continue
            self.write(line)
            now = time.monotonic()
            if now - last_flush >= flush_interval:
                try:
                    self.flush()
                except Exception as e:
                    logger.error("定时日志追加失败: %s", str(e))
                last_flush = now
        logger.info("日志采集结束，执行最终上传，obj: %s", self.log_object)
        return self.close()

    # ------------------------------------------------------------------ 读取

    def error_tail(self, limit: int = 1000) -> str:
        """返回尾部日志文本（截断到 limit 字符），供任务失败时的 errorMessage 使用。

        只基于环形缓冲，不全量缓存/拼接全部日志。CFL 模式下缓冲保存规范头行。
        """
        text = "".join(self._buffer)
        if len(text) > limit:
            return text[-limit:]
        return text

    def pending_bytes(self) -> int:
        """尚未上传到 MinIO 的本地字节数（供监控/调试）"""
        try:
            size = os.path.getsize(self.local_log_path) if os.path.exists(self.local_log_path) else 0
        except Exception:
            size = 0
        return max(0, size - self._uploaded_offset)
