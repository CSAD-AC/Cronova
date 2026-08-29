package com.sjdt.scheduler.common;

/**
 * Redis Key 常量类
 * 用于定义系统中所有Redis缓存的键名常量
 */
public class RedisKey {

    /**
     * Worker 心跳前缀 (String类型)
     * Key: scheduler:worker:heartbeat:{workerId}
     * Value: "online"
     */
    public static final String WORKER_HEARTBEAT_PREFIX = "scheduler:worker:heartbeat:";

    /**
     * Worker 实时负载前缀 (Hash类型)
     * Key: scheduler:worker:info:{workerId}
     */
    public static final String WORKER_INFO_PREFIX = "scheduler:worker:info:";

    /**
     * 调度扫描分布式锁 (String类型, SET NX EX)
     * Key: scheduler:lock:scan
     * Value: 持有锁的调度实例 token（UUID），释放时用 Lua 校验 token，防止误删他人锁
     */
    public static final String SCHEDULER_LOCK_SCAN = "scheduler:lock:scan";

    /**
     * 脚本信息缓存名称
     */
    public static final String SCRIPT_CACHE = "scheduler:cache:scripts";

    /**
     * AKSK 信息缓存名称
     */
    public static final String AKSK_CACHE = "scheduler:cache:aksk";

    /**
     * 生成完整的 Worker 心跳 Key
     */
    public static final String getWorkerHeartbeatKey(String workerId) {
        return WORKER_HEARTBEAT_PREFIX + workerId;
    }

    /**
     * 生成完整的 Worker 信息 Key
     */
    public static final String getWorkerInfoKey(String workerId) {
        return WORKER_INFO_PREFIX + workerId;
    }
}
