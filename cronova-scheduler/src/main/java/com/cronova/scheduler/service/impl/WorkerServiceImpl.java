package com.cronova.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cronova.scheduler.domain.enums.WorkerStatus;
import com.cronova.scheduler.domain.po.AKSK;
import com.cronova.scheduler.domain.po.Tag;
import com.cronova.scheduler.domain.po.Worker;
import com.cronova.scheduler.domain.po.WorkerTag;
import com.cronova.scheduler.mapper.TagMapper;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.WorkerMapper;
import com.cronova.scheduler.mapper.WorkerTagMapper;
import com.cronova.scheduler.service.IAKSKService;
import com.cronova.scheduler.service.ITaskService;
import com.cronova.scheduler.service.IWorkerService;
import com.cronova.scheduler.service.IWorkerTagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
    private IWorkerTagService workerTagService;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private WorkerTagMapper workerTagMapper;

    @Autowired
    private TaskMapper taskMapper;

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
        
        log.info("开始删除关联的AKSK记录，WorkerID: {}", id);
        akskService.lambdaUpdate()
                .eq(AKSK::getWorkerId, id)
                .remove();
        log.info("关联的AKSK记录删除成功，WorkerID: {}", id);

        log.info("开始删除关联的WorkerTag记录，WorkerID: {}", id);
        workerTagService.lambdaUpdate()
                .eq(com.cronova.scheduler.domain.po.WorkerTag::getWorkerId, id)
                .remove();
        log.info("关联的WorkerTag记录删除成功，WorkerID: {}", id);

        log.info("开始更新关联的任务记录，将target_worker设置为null，WorkerID: {}", id);
        taskMapper.clearTargetWorkerByWorkerId(id);
        log.info("关联的任务记录更新成功，WorkerID: {}", id);

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

    @Override
    public List<Worker> findOnlineWorkersByTags(Set<String> requiredTags) {
        if (requiredTags == null || requiredTags.isEmpty()) {
            log.info("任务未指定标签要求，查询所有在线Worker");
            return this.list(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Worker>()
                            .eq(Worker::getStatus, WorkerStatus.online)
            );
        }

        log.info("查询符合条件的在线Worker， requiredTags: {}", requiredTags);

        List<Tag> tags = tagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tag>()
                        .in(Tag::getName, requiredTags)
        );

        if (tags.isEmpty()) {
            log.warn("未找到匹配的标签， requiredTags: {}", requiredTags);
            return Collections.emptyList();
        }

        Set<Integer> tagIds = tags.stream()
                .map(Tag::getId)
                .collect(Collectors.toSet());

        List<String> workerIds = workerTagMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkerTag>()
                        .in(WorkerTag::getTagId, tagIds)
        ).stream()
                .map(WorkerTag::getWorkerId)
                .collect(Collectors.toList());

        if (workerIds.isEmpty()) {
            log.warn("未找到拥有指定标签的Worker");
            return Collections.emptyList();
        }

        Map<String, Long> workerIdCount = workerIds.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));

        Set<String> matchedWorkerIds = workerIdCount.entrySet().stream()
                .filter(entry -> entry.getValue() == tagIds.size())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        if (matchedWorkerIds.isEmpty()) {
            log.warn("没有Worker同时拥有所有要求的标签");
            return Collections.emptyList();
        }

        List<Worker> matchedWorkers = this.listByIds(matchedWorkerIds);
        List<Worker> onlineWorkers = matchedWorkers.stream()
                .filter(w -> w.getStatus() == WorkerStatus.online)
                .collect(Collectors.toList());

        log.info("找到 {} 个符合条件的在线Worker", onlineWorkers.size());
        return onlineWorkers;
    }

}