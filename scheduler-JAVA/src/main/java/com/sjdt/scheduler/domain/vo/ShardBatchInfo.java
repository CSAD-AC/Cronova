package com.sjdt.scheduler.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分片批次信息：片列表 + 状态 + 是否终态。
 */
@Data
public class ShardBatchInfo {

    private List<ShardInfo> shards;
    private int shardTotal;
    private String taskId;
    private boolean settled;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShardInfo {
        /** 分片序号 */
        private int index;
        /** 该片最新 run 的 id */
        private String runId;
        /** 该片最新 run 状态名 */
        private String state;
        /** 该片最新 run 耗时（毫秒） */
        private Long durationMs;
        /** 该片日志 MinIO 路径 */
        private String logPath;
    }
}
