package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.enums.WorkerStatus;
import com.sjdt.scheduler.domain.po.AKSK;
import com.sjdt.scheduler.domain.po.Worker;
import com.sjdt.scheduler.mapper.WorkerMapper;
import com.sjdt.scheduler.service.IAKSKService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.IWorkerService;
import com.sjdt.scheduler.service.IWorkerTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Worker Service 实现类
 * 提供 Worker 执行器的管理和操作的具体实现
 */
@Service
@Slf4j
public class WorkerServiceImpl extends ServiceImpl<WorkerMapper, Worker> implements IWorkerService {

    @Autowired
    private IAKSKService akskService;

    @Autowired
    private ITaskService taskService;

    @Autowired
    private IWorkerTagService workerTagService;

    /**
     * 根据主机名或 IP 查询 Worker
     * @param host 主机名
     * @param ip IP 地址
     * @return Worker 实体
     */
    @Override
    public Worker getByHostOrIp(String host, String ip) {
        log.info("根据主机名或IP查询Worker，主机名: {}, IP: {}", host, ip);
        
        Worker worker = this.lambdaQuery()
                .eq(Worker::getHost, host)
                .or()
                .eq(Worker::getIp, ip)
                .one();
        
        if (worker != null) {
            log.info("查询到Worker，ID: {}, 主机名: {}, IP: {}", worker.getId(), worker.getHost(), worker.getIp());
        } else {
            log.info("未查询到Worker，主机名: {}, IP: {}", host, ip);
        }
        return worker;
    }

    /**
     * 更新 Worker 心跳信息
     * @param workerId Worker ID
     */
    @Override
    public void updateHeartbeat(String workerId) {
        log.info("开始更新Worker心跳，WorkerID: {}", workerId);
        
        Worker worker = this.getById(workerId);
        if (worker != null) {
            worker.setStatus(WorkerStatus.online);
            worker.setLastHeartbeat(LocalDateTime.now());
            this.updateById(worker);
            log.info("Worker心跳更新成功，WorkerID: {}", workerId);
        } else {
            log.warn("Worker不存在，无法更新心跳，WorkerID: {}", workerId);
        }
    }

    /**
     * 将 Worker 标记为离线
     * @param workerId Worker ID
     */
    @Override
    public void markOffline(String workerId) {
        log.info("开始将Worker标记为离线，WorkerID: {}", workerId);
        
        Worker worker = this.getById(workerId);
        if (worker != null) {
            worker.setStatus(WorkerStatus.offline);
            this.updateById(worker);
            log.info("Worker标记为离线成功，WorkerID: {}", workerId);
        } else {
            log.warn("Worker不存在，无法标记为离线，WorkerID: {}", workerId);
        }
    }

    /**
     * 更新 Worker 主机名
     * @param id Worker ID
     * @param host 新的主机名
     */
    @Override
    public void updateHostById(String id, String host) {
        log.info("开始更新Worker主机名，WorkerID: {}, 新主机名: {}", id, host);
        
        Worker worker = this.getById(id);
        if (worker != null) {
            worker.setHost(host);
            worker.setUpdateTime(LocalDateTime.now());
            this.updateById(worker);
            log.info("Worker主机名更新成功，WorkerID: {}, 新主机名: {}", id, host);
        } else {
            log.warn("Worker不存在，无法更新主机名，WorkerID: {}", id);
        }
    }

    /**
     * 删除 Worker
     * 同步删除关联的 AKSK、WorkerTag 和相关任务
     * @param id Worker ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorker(String id) {
        log.info("开始删除Worker，WorkerID: {}", id);
        
        // 1. 删除关联的 AKSK 记录
        log.info("开始删除关联的AKSK记录，WorkerID: {}", id);
        akskService.lambdaUpdate()
                .eq(AKSK::getWorkerId, id)
                .remove();
        log.info("关联的AKSK记录删除成功，WorkerID: {}", id);

        // 2. 删除关联的 WorkerTag 记录
        log.info("开始删除关联的WorkerTag记录，WorkerID: {}", id);
        workerTagService.lambdaUpdate()
                .eq(com.sjdt.scheduler.domain.po.WorkerTag::getWorkerId, id)
                .remove();
        log.info("关联的WorkerTag记录删除成功，WorkerID: {}", id);

        // 3. 更新关联的任务记录，将 target_worker 设置为 null
        log.info("开始更新关联的任务记录，将target_worker设置为null，WorkerID: {}", id);
        taskService.updateTargetWorkerToNullByWorkerId(id);
        log.info("关联的任务记录更新成功，WorkerID: {}", id);

        // 4. 删除 Worker 记录
        log.info("开始删除Worker记录，WorkerID: {}", id);
        this.removeById(id);
        log.info("Worker删除成功，WorkerID: {}", id);
    }

    @Override
    public long countOnlineWorkers() {
        return this.count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Worker>()
                        .eq(Worker::getStatus, WorkerStatus.online)
        );
    }

}