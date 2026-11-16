ALTER TABLE task_run
    ADD INDEX idx_run_status_heartbeat (status, last_heartbeat_time),
    ADD INDEX idx_run_task_created (task_id, create_time),
    ADD INDEX idx_run_batch_shard (shard_batch_id, shard_index, retry_num),
    ADD INDEX idx_run_worker (worker_id);

ALTER TABLE task_log
    ADD INDEX idx_log_run (run_id),
    ADD INDEX idx_log_batch_shard (shard_batch_id, shard_index);
