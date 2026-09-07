package com.sjdt.worker.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CFL（Canonical Log Line）标准日志格式化器（Java 执行器版，对应 Python cfl.py）。
 * <p>
 * 规范行：
 * <pre>
 * 2026-09-05T04:25:27.123Z | shard 03/08 | ERROR    | worker.py:run:42 - 消息
 * </pre>
 * 归一化输入：已归一化 CFL 行（幂等）/ sjdt_utils 统一格式 / Java [LEVEL] 前缀 / 裸 print；
 * 堆栈与缩进续行归属上一条目（共用首行时间戳），归并排序单元 = 条目。
 */
public class Cfl {

    private static final Pattern CFL_RE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z)"
                    + "(?:\\s*\\|\\s*(shard \\d+/\\d+))?"
                    + "\\s*\\|\\s*(\\w+)\\s*\\|\\s*([^|]*?)(?:\\s*-\\s*)(.*)$");
    private static final Pattern SJDT_RE = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})"
                    + "\\s*\\|\\s*(\\w+)\\s*\\|\\s*([^|]*?)(?:\\s*-\\s*)(.*)$");
    private static final Pattern JAVA_LEVEL_RE = Pattern.compile("^.*?\\[([A-Z]+)\\]");
    private static final Pattern STACK_PY = Pattern.compile(
            "^\\s*Traceback \\(most recent call last\\):|^\\s+File \"|^\\s+[A-Za-z_][\\w.]*Error:");
    private static final Pattern STACK_JAVA = Pattern.compile(
            "^\\s+at\\s+|^\\s*Caused by:|^\\s*Exception in thread");
    private static final Pattern INDENT_RE = Pattern.compile("^[\\s\\t]");

    private static final DateTimeFormatter UTC_MS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter SJDT_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String[] LEVELS = {"DEBUG", "INFO", "WARN", "ERROR", "CRITICAL"};

    private final Integer shardIndex;
    private final Integer shardTotal;
    private Entry open;

    public Cfl(Integer shardIndex, Integer shardTotal) {
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal;
    }

    /** 一个规范条目（可含多行物理行，堆栈归属首行） */
    public static class Entry {
        public final long tsMillis;
        public final String level;
        public final String location;
        public final String message;
        public final List<String> lines = new ArrayList<>();

        public Entry(long tsMillis, String level, String location, String message) {
            this.tsMillis = tsMillis;
            this.level = level;
            this.location = location;
            this.message = message;
            this.lines.add(message);
        }

        public String levelAbbr() {
            return level.isEmpty() ? "I" : level.substring(0, 1);
        }
    }

    /** 输入一行原始输出；新条目返回（lines 仅含头行），续行返回 null */
    public Entry process(String rawLine, long receiveTsMillis) {
        String line = rawLine.endsWith("\n") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty()) {
            if (open != null) {
                open.lines.add("");
            }
            return null;
        }
        if (open != null && isContinuation(line)) {
            open.lines.add(line);
            return null;
        }
        open = parse(line, receiveTsMillis);
        return open;
    }

    /** 流结束：返回当前未完成条目 */
    public Entry flush() {
        Entry e = open;
        open = null;
        return e;
    }

    /** 条目头行 → CFL 文本（不含换行） */
    public String formatHeader(Entry e) {
        return formatTs(e.tsMillis) + " | " + formatShard() + " | " + padLevel(e.level)
                + " | " + e.location + " - " + e.message;
    }

    // ------------------------------------------------------------------ 内部

    private boolean isContinuation(String line) {
        if (line.startsWith("shard ") || CFL_RE.matcher(line).matches()) {
            return false; // 新 CFL 头行
        }
        return STACK_PY.matcher(line).find() || STACK_JAVA.matcher(line).find()
                || INDENT_RE.matcher(line).find();
    }

    private Entry parse(String line, long receiveTsMillis) {
        long ts = receiveTsMillis;
        String level = "INFO";
        String loc = "";
        String msg = line;

        Matcher cfl = CFL_RE.matcher(line);
        if (cfl.matches()) {
            ts = isoTsToMillis(cfl.group(1));
            level = normLevel(cfl.group(3));
            loc = cfl.group(4).trim();
            msg = cfl.group(5);
        } else {
            Matcher sjdt = SJDT_RE.matcher(line);
            if (sjdt.matches()) {
                ts = sjdtTsToMillis(sjdt.group(1), receiveTsMillis);
                level = normLevel(sjdt.group(2));
                loc = sjdt.group(3).trim();
                msg = sjdt.group(4);
            } else {
                Matcher jl = JAVA_LEVEL_RE.matcher(line);
                if (jl.find()) {
                    String lv = jl.group(1);
                    if (isLevel(lv)) {
                        level = lv;
                    }
                } else {
                    level = inferLevel(line);
                }
            }
        }

        Entry entry = new Entry(ts, level, loc, msg);
        entry.lines.set(0, line); // 头行原样（含前缀）保留在 lines[0]，message 单独存
        return entry;
    }

    private static boolean isLevel(String s) {
        for (String l : LEVELS) {
            if (l.equals(s)) {
                return true;
            }
        }
        return false;
    }

    private static String normLevel(String level) {
        String up = level.toUpperCase();
        switch (up) {
            case "WARNING":
                return "WARN";
            case "CRIT":
            case "FATAL":
                return "CRITICAL";
            case "TRACE":
                return "DEBUG";
            default:
                return isLevel(up) ? up : "INFO";
        }
    }

    private static String inferLevel(String line) {
        String low = line.toLowerCase();
        if (low.contains("traceback") || low.contains("exception") || low.contains("fatal")
                || low.contains("critical") || low.contains("error")) {
            return "ERROR";
        }
        if (low.contains("warning") || low.contains("warn")) {
            return "WARN";
        }
        if (low.contains("debug")) {
            return "DEBUG";
        }
        return "INFO";
    }

    private String formatShard() {
        if (shardIndex == null || shardTotal == null || shardTotal <= 0) {
            return "";
        }
        int width = Math.max(2, String.valueOf(shardTotal).length());
        return String.format("shard %0" + width + "d/%0" + width + "d", shardIndex, shardTotal);
    }

    private static String padLevel(String level) {
        StringBuilder sb = new StringBuilder(level);
        while (sb.length() < 8) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String formatTs(long millis) {
        return UTC_MS.format(Instant.ofEpochMilli(millis));
    }

    private static long isoTsToMillis(String iso) {
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return 0;
        }
    }

    private static long sjdtTsToMillis(String local, long fallback) {
        try {
            return LocalDateTime.parse(local, SJDT_TS)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return fallback;
        }
    }
}
