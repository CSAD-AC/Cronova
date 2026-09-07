-- =============================================================
-- 调度内核高可用 —— DDL 一次性加列
-- 文件：sql/upgrade_phase1_ha.sql
-- 说明：
--   1. 对存量库执行本脚本一次（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
--      重复执行会报 Duplicate column，属预期行为，手工判断即可）
--   2. 全新安装请直接使用已同步新列的 cronova-scheduler-init.sql
--   3. 一次性加齐后续要用的列（路由/分片/可观测），避免反复改表
-- =============================================================

-- ---------- tasks：触发落库 + 路由/阻塞/分片配置 ----------
ALTER TABLE tasks
    ADD COLUMN next_trigger_time DATETIME(3) NULL
        COMMENT '下次理论触发时间（驱动到期扫描，CAS 抢占依据）' AFTER update_time,
    ADD COLUMN last_trigger_time DATETIME(3) NULL
        COMMENT '上次实际触发时间' AFTER next_trigger_time,
    ADD COLUMN route_strategy VARCHAR(32) NOT NULL DEFAULT 'LEAST_LOAD'
        COMMENT 'ROUND_ROBIN/RANDOM/CONSISTENT_HASH/LEAST_LOAD/FAILOVER/TAG_MATCH/SHARD'
        AFTER last_trigger_time,
    ADD COLUMN block_strategy VARCHAR(16) NOT NULL DEFAULT 'DISCARD_LATER'
        COMMENT 'SERIAL/DISCARD_LATER/COVER_EARLY' AFTER route_strategy,
    ADD COLUMN shard_total INT NULL
        COMMENT '分片总数,NULL/0=不分片' AFTER block_strategy,
    ADD INDEX idx_due (status, task_type, next_trigger_time);

-- ---------- task_run：分片身份 + 触发延迟埋点 ----------
ALTER TABLE task_run
    ADD COLUMN shard_index INT NULL
        COMMENT '分片序号' AFTER last_heartbeat_time,
    ADD COLUMN shard_total INT NULL
        COMMENT '本批分片总数' AFTER shard_index,
    ADD COLUMN shard_batch_id VARCHAR(64) NULL
        COMMENT '同一次触发的分片批次ID' AFTER shard_total,
    ADD COLUMN dispatch_time DATETIME(3) NULL
        COMMENT '实际派发时间' AFTER shard_batch_id,
    ADD COLUMN trigger_latency_ms BIGINT NULL
        COMMENT '派发时间-理论触发时间' AFTER dispatch_time;
