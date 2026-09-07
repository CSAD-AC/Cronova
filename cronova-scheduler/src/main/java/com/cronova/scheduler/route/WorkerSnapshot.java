package com.cronova.scheduler.route;

import com.cronova.scheduler.domain.po.Worker;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;

/**
 * Worker 在线快照：路由决策的输入
 * <p>
 * id / host / tags 来自数据库；负载字段来自 Redis 心跳信息 Hash
 * （{@code scheduler:worker:info:{workerId}}，Worker 每 10s 上报一次）。
 *
 * @param id                 Worker ID
 * @param host               主机名
 * @param tags               标签名集合
 * @param cpuUsage           CPU 使用率（0-100）
 * @param memUsage           内存使用率（0-100）
 * @param runningTasks       当前运行任务数
 * @param maxConcurrent      最大并发数（当前取固定保守值）
 * @param lastHeartbeatEpochMs 最近心跳时间（epoch ms），用于 FAILOVER 排序
 */
public record WorkerSnapshot(
        String id,
        String host,
        Set<String> tags,
        double cpuUsage,
        double memUsage,
        int runningTasks,
        int maxConcurrent,
        long lastHeartbeatEpochMs) {

    /** 默认最大并发数：无配置时的保守取值（对齐 Worker 端线程池上限） */
    public static final int DEFAULT_MAX_CONCURRENT = 10;

    /** 未知负载时的保守默认：CPU 视为 0（无害），running 视为最大（不可选） */
    private static final double UNKNOWN_CPU = 0;
    private static final int UNKNOWN_RUNNING = Integer.MAX_VALUE;

    public static WorkerSnapshot from(Worker worker, Set<String> tags, Map<Object, Object> info) {
        double cpu = parseDouble(info.get("cpuUsage"), UNKNOWN_CPU);
        double mem = parseDouble(info.get("memUsage"), 0);
        int running = parseInt(info.get("runningTasks"), UNKNOWN_RUNNING);
        long heartbeat = parseEpochMs(info.get("lastHeartbeat"), 0L); // 缺失视为最旧，FAILOVER 优先真正心跳新者
        return new WorkerSnapshot(worker.getId(), worker.getHost(), tags, cpu, mem, running,
                DEFAULT_MAX_CONCURRENT, heartbeat);
    }

    private static double parseDouble(Object v, double def) {
        try {
            if (v instanceof Number n) {
                return n.doubleValue();
            }
            return v == null ? def : Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseInt(Object v, int def) {
        try {
            if (v instanceof Number n) {
                return n.intValue();
            }
            return v == null ? def : Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static long parseEpochMs(Object v, long def) {
        try {
            if (v instanceof Number n) {
                return n.longValue();
            }
            if (v == null) {
                return def;
            }
            // Worker 上报的 lastHeartbeat 是 LocalDateTime.toString() 格式
            return LocalDateTime.parse(String.valueOf(v))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
        } catch (Exception e) {
            return def;
        }
    }
}
