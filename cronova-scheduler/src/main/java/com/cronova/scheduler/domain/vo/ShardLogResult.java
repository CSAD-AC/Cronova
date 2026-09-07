package com.cronova.scheduler.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分片批次日志聚合结果（运行中游标归并）。
 * <p>
 * 无状态游标协议：前端下次请求原样带回 cursor，服务端据此逐片续读。
 * entries 按 (时间戳, shardIndex) 稳定归并；settled=true 表示批次已终态。
 */
@Data
public class ShardLogResult {

    /** 不透明游标（Base64(JSON {"offsets":{shardIndex:offset}}))，前端下次原样带回 */
    private String cursor;

    /** 按时间稳定归并的条目（可含多行续行） */
    private List<Entry> entries;

    /** 各片读取进度与状态 */
    private List<ShardStatus> shardStatus;

    /** 批次是否已终态（全部片 success/failed/timeout） */
    private boolean settled;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Entry {
        /** 头行 ISO-8601 UTC 毫秒时间戳 */
        private String ts;
        /** 分片序号（无 shard 字段为 -1） */
        private int shard;
        /** 级别 */
        private String level;
        /** 物理行（头行 + 续行） */
        private List<String> lines;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShardStatus {
        /** 分片序号 */
        private int index;
        /** 该片最新 run 状态名 */
        private String state;
        /** 已消费字节偏移 */
        private long offset;
        /** 对象总字节数 */
        private long size;
    }
}
