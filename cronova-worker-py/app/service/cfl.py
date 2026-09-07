"""CFL（Canonical Log Line）标准日志格式化组件。

规范行格式：
    2026-09-05T04:25:27.123Z | shard 03/08 | ERROR    | worker.py:run:42 - 数据库连接失败：timeout
    └──── 24字符 ISO-8601 UTC 毫秒 ────┘  └─ 固定宽 ─┘ └ 8宽 ┘ └ 位置(可空) ┘ └ 原始内容逐字保留 ┘

- 时间戳固定宽度 → 字典序即时间序，跨片归并直接按字符串比较；
- shard 字段可选（非分片省略），index/total 零填充两位（>99 自动加宽）；
- 级别 8 宽（DEBUG/INFO/WARN/ERROR/CRITICAL）；
- 消息体逐字保留（非破坏性原则）。

归一化规则（捕获时逐行执行）：
    1. 幂等：已是 CFL 的行直接解析；
    2. 命中既有 cronova_utils 统一格式（ts | LEVEL | loc - msg）→ 时间转 UTC 毫秒；
    3. 命中第三方前缀（Java [LEVEL] 等）→ 提取可识别字段；
    4. 裸 print → worker 读取时刻 + 关键词推断级别（推断不出 → INFO）。

条目分组：堆栈/续行（Traceback、  File "…"、缩进、\\tat、Caused by:、Exception in thread）
归属上一条目，共用首行时间戳。归并的排序单元 = 条目（可多行物理行）。
"""
import re
from datetime import datetime, timezone
from typing import List, Optional

LEVEL_WIDTH = 8

# 级别别名归一（WARNING→WARN、CRIT/FATAL→CRITICAL、TRACE→DEBUG）
LEVEL_ALIAS = {
    "WARNING": "WARN",
    "WARN": "WARN",
    "CRIT": "CRITICAL",
    "FATAL": "CRITICAL",
    "TRACE": "DEBUG",
}
_LEVELS = ("DEBUG", "INFO", "WARN", "ERROR", "CRITICAL")

# 已归一化的 CFL 行（幂等解析）
_CFL_RE = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z)"
    r"(?:\s*\|\s*(?P<shard>shard \d+/\d+))?"
    r"\s*\|\s*(?P<level>\w+)\s*\|\s*(?P<loc>[^|]*?)(?:\s*-\s*)(?P<msg>.*)$"
)

# 既有 cronova_utils 统一格式：2026-02-04 14:25:27 | INFO | module.py:function:123 - msg
_CRONOVA_RE = re.compile(
    r"^(?P<ts>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})\s*\|\s*(?P<level>\w+)\s*\|\s*(?P<loc>[^|]*?)(?:\s*-\s*)(?P<msg>.*)$"
)

# Java logback/log4j：2026-09-05 04:25:27,123 [INFO ] ... / 04:25:27.123 [INFO]
_JAVA_LEVEL_RE = re.compile(r"^.*?\[(?P<level>[A-Z]+)\]")

# 堆栈/续行识别（归属上一条目）
_STACK_PY = re.compile(r"^\s*Traceback \(most recent call last\):|^\s+File \"|^\s+[A-Za-z_][\w.]*Error:")
_STACK_JAVA = re.compile(r"^\s+at\s+|^\s*Caused by:|^\s*Exception in thread")
_INDENT_RE = re.compile(r"^[\s\t]")

# 级别推断关键词
_ERROR_KEYWORDS = ("traceback", "exception", "fatal", "critical", "error")
_WARN_KEYWORDS = ("warning", "warn")
_DEBUG_KEYWORDS = ("debug",)


def _pad_level(level: str) -> str:
    return level.ljust(LEVEL_WIDTH)


def _parse_ts_to_millis(ts: str) -> int:
    """解析 cronova_utils 本地时间 → UTC 毫秒时间戳。"""
    try:
        dt = datetime.strptime(ts, "%Y-%m-%d %H:%M:%S")
        # 视作本地时间，转 UTC（worker 容器通常 UTC；时区由环境决定）
        return int(dt.astimezone(timezone.utc).timestamp() * 1000)
    except ValueError:
        return 0


def format_ts(millis: int) -> str:
    """毫秒时间戳 → 24 字符 ISO-8601 UTC（字典序即时间序）"""
    return datetime.fromtimestamp(millis / 1000, tz=timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def format_shard(index: Optional[int], total: Optional[int]) -> str:
    """shard 字段：`shard 03/08`；index/total 零填充两位，>99 自动加宽；非分片返回空串。"""
    if index is None or total is None or total <= 0:
        return ""
    width = max(2, len(str(total)))
    return f"shard {index:0{width}d}/{total:0{width}d}"


class CflEntry:
    """一个规范条目（可含多行物理行，堆栈归属首行）。"""

    __slots__ = ("ts_millis", "level", "location", "message", "lines", "seq")

    def __init__(self, ts_millis: int, level: str, location: str, message: str,
                 lines: List[str], seq: int = 0):
        self.ts_millis = ts_millis
        self.level = level
        self.location = location
        self.message = message
        self.lines = lines  # 物理行（首行 + 续行）
        self.seq = seq  # 片内条目序号（稳定排序键）

    def level_abbr(self) -> str:
        return self.level[0] if self.level else "I"


class CflFormatter:
    """有状态 CFL 归一化器：逐行 process，新条目即返回，续行返回 None。

    用法（单写线程）：
        fmt = CflFormatter(shard_index=3, shard_total=8)
        while (line := reader.readline()):
            entry = fmt.process(line, receive_ts_millis)
            if entry is not None:
                write(fmt.format_header(entry))   # 头行（含偏移/idx 记录由调用方维护）
            else:
                write(line)                       # 续行原样落盘
        tail = fmt.flush()                        # 流结束取当前条目（可选）
    """

    def __init__(self, shard_index: Optional[int] = None, shard_total: Optional[int] = None):
        self.shard_index = shard_index
        self.shard_total = shard_total
        self._open: Optional[CflEntry] = None
        self._seq = 0

    def process(self, raw_line: str, receive_ts_millis: int) -> Optional[CflEntry]:
        """输入一行原始输出。

        - 新条目：解析头行，打开条目并返回之（lines 仅含头行）；
        - 续行/堆栈行/缩进行：追加到当前条目（更新其 lines），返回 None；
        - 空行：追加到当前条目（若有），返回 None。
        """
        stripped = raw_line.rstrip("\r\n")
        if not stripped:
            # 空行：归属当前条目（若有），否则忽略，不开启新条目
            if self._open is not None:
                self._open.lines.append("")
            return None
        if self._open is not None and self._is_continuation(stripped):
            self._open.lines.append(stripped)
            return None
        self._open = self._parse(stripped, receive_ts_millis)
        return self._open

    def flush(self) -> Optional[CflEntry]:
        """流结束：返回当前未完成的条目（此后 process 不应再调用）。"""
        entry = self._open
        self._open = None
        return entry

    def format_header(self, entry: CflEntry) -> str:
        """条目头行 → CFL 文本（不含换行）。"""
        return (f"{format_ts(entry.ts_millis)}"
                f" | {format_shard(self.shard_index, self.shard_total)}"
                f" | {_pad_level(entry.level)}"
                f" | {entry.location} - {entry.message}")

    def format(self, entry: CflEntry) -> str:
        """条目 → CFL 完整文本（头行 + 续行，含换行）。"""
        header = self.format_header(entry)
        if len(entry.lines) <= 1:
            return header + "\n"
        body = "\n".join(entry.lines[1:])
        return header + "\n" + body + "\n"

    # ------------------------------------------------------------------ 内部

    def _is_continuation(self, line: str) -> bool:
        if line.startswith("shard ") or _CFL_RE.match(line):
            return False  # 新 CFL 头行，不是续行
        return bool(_STACK_PY.match(line) or _STACK_JAVA.match(line) or _INDENT_RE.match(line))

    def _parse(self, line: str, receive_ts_millis: int) -> CflEntry:
        ts, level, loc, msg = receive_ts_millis, "INFO", "", line

        m = _CFL_RE.match(line)
        if m:
            # 幂等：已是 CFL 行
            ts = _iso_ts_to_millis(m.group("ts"))
            level = self._norm_level(m.group("level"))
            loc = m.group("loc").strip()
            msg = m.group("msg")
        else:
            m2 = _CRONOVA_RE.match(line)
            if m2:
                ts = _parse_ts_to_millis(m2.group("ts")) or receive_ts_millis
                level = self._norm_level(m2.group("level"))
                loc = m2.group("loc").strip()
                msg = m2.group("msg")
            else:
                jm = _JAVA_LEVEL_RE.match(line)
                if jm and jm.group("level") in _LEVELS:
                    level = jm.group("level")
                else:
                    level = self._infer_level(line)

        entry = CflEntry(ts, level, loc, msg, [line], self._seq)
        self._seq += 1
        return entry

    def _norm_level(self, level: str) -> str:
        up = level.upper()
        return LEVEL_ALIAS.get(up, up if up in _LEVELS else "INFO")

    @staticmethod
    def _infer_level(line: str) -> str:
        low = line.lower()
        if any(k in low for k in _ERROR_KEYWORDS):
            return "ERROR"
        if any(k in low for k in _WARN_KEYWORDS):
            return "WARN"
        if any(k in low for k in _DEBUG_KEYWORDS):
            return "DEBUG"
        return "INFO"

    def format(self, entry: CflEntry) -> str:
        """条目 → CFL 文本（首行规范行 + 续行原样）。"""
        header = (f"{format_ts(entry.ts_millis)}"
                  f" | {format_shard(self.shard_index, self.shard_total)}"
                  f" | {_pad_level(entry.level)}"
                  f" | {entry.location} - {entry.message}")
        if len(entry.lines) <= 1:
            return header + "\n"
        body = "\n".join(entry.lines[1:])
        return header + "\n" + body + "\n"


def _iso_ts_to_millis(iso: str) -> int:
    try:
        dt = datetime.strptime(iso, "%Y-%m-%dT%H:%M:%S.%fZ").replace(tzinfo=timezone.utc)
        return int(dt.timestamp() * 1000)
    except ValueError:
        return 0
