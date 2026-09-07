package com.cronova.worker.service;

import java.util.ArrayDeque;

/**
 * 任务日志管道（Java 执行器版）：尾部环形缓冲 + 已上传偏移。
 * <p>
 * 对应 Python 执行器 app/service/task_log_pipeline.py。本版本不拆分线程
 * （AbstractProcessHandler 为单线程流式读取），因此只承载：
 * 1. 尾部 N 行环形缓冲（内存有界）；
 * 2. errorMessage 尾部文本提取。
 */
public class TaskLogPipeline {

    private final ArrayDeque<String> ring;
    private final int tailLines;
    private long uploadedOffset = 0;

    public TaskLogPipeline(int tailLines) {
        this.tailLines = Math.max(1, tailLines);
        this.ring = new ArrayDeque<>(this.tailLines);
    }

    /** 追加一行日志到环形缓冲（超出 tailLines 自动丢弃最旧行） */
    public void add(String line) {
        synchronized (ring) {
            if (ring.size() == tailLines) {
                ring.removeFirst();
            }
            ring.addLast(line);
        }
    }

    /** 返回尾部日志文本（截断到 limit 字符），用于任务失败时的 errorMessage */
    public String errorTail(int limit) {
        StringBuilder sb = new StringBuilder();
        synchronized (ring) {
            for (String s : ring) {
                sb.append(s).append('\n');
            }
        }
        String text = sb.toString();
        return text.length() > limit ? text.substring(text.length() - limit) : text;
    }

    public long getUploadedOffset() {
        return uploadedOffset;
    }

    public void setUploadedOffset(long uploadedOffset) {
        this.uploadedOffset = uploadedOffset;
    }
}
