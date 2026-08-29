package com.sjdt.scheduler.route.strategy;

import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.route.WorkerSnapshot;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一致性哈希策略：同一 taskId 尽量命中同一 Worker，Worker 增减时迁移最小
 * <p>
 * 每个 Worker 生成 160 个虚拟节点打散热点；用 taskId 的 MD5 哈希在环上顺时针找第一个节点。
 * 使用 MD5 而非 Object.hashCode()，保证跨 JVM、跨实例计算结果一致（hashCode 不保证稳定）。
 */
@Component
public class ConsistentHashStrategy implements RouteStrategy {

    public static final String NAME = "CONSISTENT_HASH";

    /** 每 Worker 虚拟节点数：160 足以在百级节点规模下打散热点 */
    private static final int VIRTUAL_NODES = 160;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public WorkerSnapshot select(Task task, List<WorkerSnapshot> candidates) {
        TreeMap<Long, WorkerSnapshot> ring = new TreeMap<>();
        for (WorkerSnapshot worker : candidates) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                ring.put(hash(worker.id() + "#" + i), worker);
            }
        }
        long keyHash = hash(task.getId());
        Map.Entry<Long, WorkerSnapshot> entry = ring.ceilingEntry(keyHash);
        if (entry == null) {
            entry = ring.firstEntry(); // 环尾回卷
        }
        return entry == null ? null : entry.getValue();
    }

    /**
     * MD5 取前 8 字节构造 64 位哈希（跨 JVM 稳定）
     */
    private long hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xffL);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}
