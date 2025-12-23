package com.sjdt.scheduler.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.service.IWorkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker 心跳检查任务
 * 定期检查 Worker 的心跳时间，将超过 25 秒未心跳的 Worker 设置为离线状态
 */
@Component
public class WorkerHeartbeatCheckTask {

    @Autowired
    private IWorkerService workerService;

    /**
     * 心跳超时时间（秒）
     */
    private static final long HEARTBEAT_TIMEOUT = 25;

    /**
     * 每 15 秒执行一次心跳检查
     */
    @Scheduled(fixedRate = 1500)
    public void checkHeartbeat() {
        // 获取当前时间
        LocalDateTime now = LocalDateTime.now();
        // 计算超时时间
        LocalDateTime timeoutTime = now.minusSeconds(HEARTBEAT_TIMEOUT);

        // 查询所有在线或忙碌但心跳超时的 Worker
        List<Worker> workers = workerService.lambdaQuery()
                .in(Worker::getStatus, WorkerStatus.online, WorkerStatus.busy)
                .lt(Worker::getLastHeartbeat, timeoutTime)
                .list();

        // 将超时的 Worker 设置为离线状态
        for (Worker worker : workers) {
            worker.setStatus(WorkerStatus.offline);
            workerService.updateById(worker);
        }
    }
}