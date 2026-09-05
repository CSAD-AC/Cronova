package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.po.TaskLog;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.domain.vo.ShardBatchInfo;
import com.sjdt.scheduler.domain.vo.ShardLogResult;
import com.sjdt.scheduler.mapper.TaskLogMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 分片批次日志聚合服务（运行中游标归并）。
 * <p>
 * 逐片 Range 增量读 → CflParser 解析条目 → 按 (tsMillis, shardIndex)
 * 稳定归并；游标由前端持有下次带回，服务端零会话状态。
 */
@Slf4j
@Service
public class ShardLogAggregateService {

    /** 读单片的字节上限（可被入参 limit 放大） */
    private static final int DEFAULT_LIMIT = 65536;
    private static final int MAX_LIMIT = 1024 * 1024;
    /** 单页最大返回条目数（防御超大日志/多片） */
    private static final int MAX_ENTRIES = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TaskRunMapper taskRunMapper;
    private final TaskLogMapper taskLogMapper;
    private final MinioClient minioClient;

    @Value("${minio.bucket-name:task-engine}")
    private String defaultBucketName;

    @Autowired
    public ShardLogAggregateService(TaskRunMapper taskRunMapper, TaskLogMapper taskLogMapper,
                                    MinioClient minioClient) {
        this.taskRunMapper = taskRunMapper;
        this.taskLogMapper = taskLogMapper;
        this.minioClient = minioClient;
    }

    /** 运行中游标归并：按时间稳定返回一批条目，并给出新游标 */
    public ShardLogResult aggregate(String batchId, String cursor, int limit, String level, Integer shard) {
        ShardLogResult result = new ShardLogResult();
        Map<Integer, TaskRun> latestPerShard = latestAttemptPerShard(batchId);
        if (latestPerShard.isEmpty()) {
            return result; // 无分片 run（调用方已在 controller 判空返回 404）
        }

        Map<Integer, Long> offsets = decodeCursor(cursor);
        int readLimit = Math.min(Math.max(limit > 0 ? limit : DEFAULT_LIMIT, 0), MAX_LIMIT);

        boolean settled = latestPerShard.values().stream().allMatch(r -> isTerminal(r.getStatus()));
        List<ShardLogResultEntry> merged = new ArrayList<>();
        List<ShardLogResult.ShardStatus> statuses = new ArrayList<>();
        Map<Integer, Long> newOffsets = new LinkedHashMap<>();

        for (Map.Entry<Integer, TaskRun> e : latestPerShard.entrySet()) {
            int idx = e.getKey();
            TaskRun run = e.getValue();
            long startOffset = offsets.getOrDefault(idx, 0L);
            readShard(run, idx, startOffset, readLimit, level, shard, merged, statuses, newOffsets);
        }

        // 按 (毫秒, 分片序号) 稳定归并
        merged.sort(Comparator.comparingLong(ShardLogResultEntry::tsMillis)
                .thenComparingInt(ShardLogResultEntry::shardIndex));

        List<ShardLogResult.Entry> entryVos = merged.stream()
                .limit(MAX_ENTRIES)
                .map(x -> new ShardLogResult.Entry(x.tsIso(), x.shardIndex(), x.level(), x.lines()))
                .collect(Collectors.toList());

        result.setEntries(entryVos);
        result.setShardStatus(statuses);
        result.setSettled(settled);
        result.setCursor(encodeCursor(newOffsets));
        return result;
    }

    /** 分片批次信息（片列表 + 状态 + 是否终态） */
    public ShardBatchInfo batchInfo(String batchId) {
        Map<Integer, TaskRun> latestPerShard = latestAttemptPerShard(batchId);
        ShardBatchInfo info = new ShardBatchInfo();
        if (latestPerShard.isEmpty()) {
            return info;
        }
        List<ShardBatchInfo.ShardInfo> shards = new ArrayList<>();
        for (Map.Entry<Integer, TaskRun> e : latestPerShard.entrySet()) {
            TaskRun run = e.getValue();
            ShardBatchInfo.ShardInfo si = new ShardBatchInfo.ShardInfo();
            si.setIndex(e.getKey());
            si.setRunId(run.getId());
            si.setState(run.getStatus() != null ? run.getStatus().name() : "");
            si.setDurationMs(run.getDurationMs());
            si.setLogPath(latestLogPath(run));
            shards.add(si);
        }
        info.setShards(shards);
        info.setShardTotal(latestPerShard.values().stream()
                .map(TaskRun::getShardTotal).filter(t -> t != null)
                .max(Integer::compareTo).orElse(shards.size()));
        info.setTaskId(latestPerShard.values().stream().map(TaskRun::getTaskId).findFirst().orElse(""));
        info.setSettled(latestPerShard.values().stream().allMatch(r -> isTerminal(r.getStatus())));
        return info;
    }

    // ------------------------------------------------------------------ 内部

    /** 读取一片的日志增量、解析条目、过滤、并入候选列表；推进该片偏移 */
    private void readShard(TaskRun run, int idx, long startOffset, int limit,
                           String level, Integer shard,
                           List<ShardLogResultEntry> merged,
                           List<ShardLogResult.ShardStatus> statuses,
                           Map<Integer, Long> newOffsets) {
        String path = latestLogPath(run);
        long size = 0;
        long offset = startOffset;
        if (path != null && !path.isBlank()) {
            String[] bo = parseBucketObject(path);
            try {
                size = minioClient.statObject(StatObjectArgs.builder()
                        .bucket(bo[0]).object(bo[1]).build()).size();
            } catch (Exception e) {
                log.debug("分片日志对象不可读，idx={}, obj={}, err={}", idx, bo[1], e.getMessage());
                size = 0;
            }

            if (size > 0) {
                long from = Math.max(0, startOffset);
                long to = Math.min(size, from + limit);
                if (from < to) {
                    byte[] data = readRange(bo[0], bo[1], from, (int) (to - from));
                    String text = new String(data, StandardCharsets.UTF_8);
                    List<CflParser.Entry> entries = CflParser.parseEntries(text);
                    if (!entries.isEmpty()) {
                        // 消费到最后一个条目的边界（即使被过滤也推进，避免重复读）
                        CflParser.Entry last = entries.get(entries.size() - 1);
                        offset = Math.min(last.endByteOffset, size);
                        if (offset < from) {
                            offset = size; // 安全兜底：防游标停滞
                        }
                        for (CflParser.Entry en : entries) {
                            if (level != null && !level.isEmpty()
                                    && !level.equalsIgnoreCase(en.level)) {
                                continue;
                            }
                            if (shard != null && en.hasShard && en.shardIndex != shard) {
                                continue;
                            }
                            merged.add(new ShardLogResultEntry(en));
                        }
                    } else if (offset <= from) {
                        offset = size; // 无条目且未推进，防死循环
                    }
                } else if (from >= size) {
                    offset = size;
                }
            }
        }
        newOffsets.put(idx, offset);
        statuses.add(new ShardLogResult.ShardStatus(idx,
                run.getStatus() != null ? run.getStatus().name() : "", offset, size));
    }

    /** 批次是否存在（供 controller 判 404） */
    public boolean batchExists(String batchId) {
        return taskRunMapper.selectCount(
                new LambdaQueryWrapper<TaskRun>().eq(TaskRun::getShardBatchId, batchId)) > 0;
    }

    /** 每片取最新一次尝试（同 shardIndex 下 createTime 最新） */
    private Map<Integer, TaskRun> latestAttemptPerShard(String batchId) {
        List<TaskRun> runs = taskRunMapper.selectList(
                new LambdaQueryWrapper<TaskRun>().eq(TaskRun::getShardBatchId, batchId));
        Map<Integer, TaskRun> latest = new LinkedHashMap<>();
        for (TaskRun r : runs) {
            Integer idx = r.getShardIndex();
            if (idx == null) {
                continue;
            }
            TaskRun cur = latest.get(idx);
            if (cur == null || r.getCreateTime() != null
                    && (cur.getCreateTime() == null || r.getCreateTime().isAfter(cur.getCreateTime()))) {
                latest.put(idx, r);
            }
        }
        return latest;
    }

    private String latestLogPath(TaskRun run) {
        List<TaskLog> logs = taskLogMapper.selectList(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getRunId, run.getId())
                        .orderByDesc(TaskLog::getCreateTime));
        return logs.isEmpty() ? null : logs.get(0).getStdoutPath();
    }

    /** 解析"bucket/object"：优先识别桶名，否则用默认桶 */
    private String[] parseBucketObject(String path) {
        String bucket = defaultBucketName != null ? defaultBucketName : "task-engine";
        String object = path;
        if (path.contains("/")) {
            String first = path.substring(0, path.indexOf('/'));
            if (first.equals(bucket)) {
                object = path.substring(path.indexOf('/') + 1);
            }
        }
        return new String[]{bucket, object};
    }

    private byte[] readRange(String bucket, String object, long offset, int len) {
        byte[] data = new byte[0];
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(object).offset(offset).length((long) len).build())) {
            data = in.readNBytes(len);
        } catch (Exception e) {
            log.warn("读取分片日志失败，obj={}, offset={}, err={}", object, offset, e.getMessage());
        }
        return data;
    }

    private boolean isTerminal(TaskRunStatus status) {
        return status == TaskRunStatus.success
                || status == TaskRunStatus.failed
                || status == TaskRunStatus.timeout;
    }

    // ------------------------------------------------------------------ 游标编解码

    @SuppressWarnings("unchecked")
    private Map<Integer, Long> decodeCursor(String cursor) {
        Map<Integer, Long> offsets = new HashMap<>();
        if (cursor == null || cursor.isBlank()) {
            return offsets;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cursor);
            Map<String, Object> root = MAPPER.readValue(raw, Map.class);
            Object o = root.get("offsets");
            if (o instanceof Map) {
                ((Map<String, Object>) o).forEach((k, v) -> {
                    try {
                        offsets.put(Integer.parseInt(k), Long.parseLong(String.valueOf(v)));
                    } catch (Exception ignore) {
                        // 跳过非法项
                    }
                });
            }
        } catch (Exception e) {
            log.warn("解析分片日志游标失败，按空游标处理：{}", e.getMessage());
        }
        return offsets;
    }

    private String encodeCursor(Map<Integer, Long> offsets) {
        try {
            Map<String, Long> m = new LinkedHashMap<>();
            offsets.forEach((k, v) -> m.put(String.valueOf(k), v));
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("offsets", m);
            return Base64.getEncoder().encodeToString(MAPPER.writeValueAsBytes(root));
        } catch (Exception e) {
            return "";
        }
    }

    /** 候选条目（保留原始 CFL 头行文本与排序键） */
    private static class ShardLogResultEntry {
        private final long tsMillis;
        private final int shardIndex;
        private final String level;
        private final String tsIso;
        private final List<String> lines;

        ShardLogResultEntry(CflParser.Entry en) {
            this.tsMillis = en.tsMillis;
            int s = en.hasShard ? en.shardIndex : -1;
            this.shardIndex = s;
            this.level = en.level;
            this.tsIso = en.lines.isEmpty() ? "" : headTs(en.lines.get(0));
            this.lines = en.lines;
        }

        private static String headTs(String head) {
            int bar = head.indexOf('|');
            return bar > 0 ? head.substring(0, bar).trim() : head;
        }

        long tsMillis() {
            return tsMillis;
        }

        int shardIndex() {
            return shardIndex;
        }

        String level() {
            return level;
        }

        String tsIso() {
            return tsIso;
        }

        List<String> lines() {
            return lines;
        }
    }
}
