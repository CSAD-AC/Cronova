package com.cronova.scheduler.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CFL 日志解析器（scheduler 侧，聚合用）。
 * <p>
 * 只负责"解析" worker 已归一化的 CFL 规范行，不做归一化。规范头行：
 * <pre>
 * 2026-09-05T04:25:27.123Z | shard 03/08 | ERROR    | worker.py:run:42 - 消息
 * </pre>
 * 条目 = 头行 + 归属续行（堆栈/缩进行），归并排序单元 = 条目。
 */
public final class CflParser {

    private static final Pattern HEAD_RE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z)"
                    + "\\s*\\|\\s*(?:(shard \\d+/\\d+)\\s*\\|\\s*)?(\\w+)\\s*\\|\\s*(.*)$");
    private static final Pattern SHARD_RE = Pattern.compile("shard (\\d+)/(\\d+)");

    private CflParser() {
    }

    /** 一个规范条目（头行 + 续行） */
    public static class Entry {
        public final long tsMillis;
        public final boolean hasShard;
        public final int shardIndex;   // 无 shard = -1
        public final int shardTotal;
        public final String level;     // 无级别 = INFO
        public final List<String> lines = new ArrayList<>();
        public long headByteOffset;    // 头行字节偏移
        public long endByteOffset;     // 条目结束字节偏移（下一头行起始 / 对象末尾）

        public Entry(long tsMillis, boolean hasShard, int shardIndex, int shardTotal,
                     String level) {
            this.tsMillis = tsMillis;
            this.hasShard = hasShard;
            this.shardIndex = shardIndex;
            this.shardTotal = shardTotal;
            this.level = level;
        }
    }

    /** 该行是否为 CFL 头行 */
    public static boolean isHead(String line) {
        return line.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z\\s*\\|.*");
    }

    /** 解析头行为条目（要求 isHead(line) 为 true；lines 先含头行） */
    public static Entry parseHead(String line) {
        Matcher m = HEAD_RE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        long ts = 0;
        try {
            ts = Instant.parse(m.group(1)).toEpochMilli();
        } catch (Exception ignore) {
            // 时间解析失败不影响聚合，用 0 兜底
        }
        boolean hasShard = false;
        int shardIndex = -1;
        int shardTotal = 0;
        if (m.group(2) != null) {
            Matcher sm = SHARD_RE.matcher(m.group(2));
            if (sm.matches()) {
                hasShard = true;
                shardIndex = Integer.parseInt(sm.group(1));
                shardTotal = Integer.parseInt(sm.group(2));
            }
        }
        String level = m.group(3);
        String rest = m.group(4);
        Entry entry = new Entry(ts, hasShard, shardIndex, shardTotal, level == null ? "INFO" : level);
        entry.lines.add(line);
        return entry;
    }

    /**
     * 解析一整段对象文本为条目列表（逐行；续行归属当前条目，字节偏移按 UTF-8 计算）。
     *
     * @param text 对象文本（UTF-8 解码，可能含跨 chunk 截断尾部，末尾不完整条目会被 clamp）
     * @return 条目列表（末尾条目可能不完整，其 endByteOffset 由调用方 clamp 到对象大小）
     */
    public static List<Entry> parseEntries(String text) {
        String[] lines = text.split("\n", -1);
        List<Entry> entries = new ArrayList<>();
        long byteOffset = 0;
        Entry current = null;
        for (String raw : lines) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            int lineBytes = line.getBytes(StandardCharsets.UTF_8).length + 1; // +1 换行符
            if (isHead(line)) {
                if (current != null) {
                    current.endByteOffset = byteOffset;
                }
                current = parseHead(line);
                if (current != null) {
                    current.headByteOffset = byteOffset;
                    entries.add(current);
                }
            } else if (current != null) {
                current.lines.add(line);
            }
            byteOffset += lineBytes;
        }
        if (current != null) {
            current.endByteOffset = byteOffset;
        }
        return entries;
    }
}
