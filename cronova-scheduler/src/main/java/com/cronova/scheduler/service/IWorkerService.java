package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.cronova.scheduler.domain.po.Worker;

import java.util.List;
import java.util.Set;

/**
 * Worker Service 接口
 * 提供 Worker 执行器的管理和操作
 */
public interface IWorkerService extends IService<Worker> {

    /**
     * 根据主机名或 IP 查询 Worker
     * @param host 主机名
     * @param ip IP 地址
     * @return Worker 实体
     */
    Worker getByHostOrIp(String host, String ip);

    /**
     * 更新 Worker 心跳信息
     * @param workerId Worker ID
     */
    void updateHeartbeat(String workerId);

    /**
     * 将 Worker 标记为离线
     * @param workerId Worker ID
     */
    void markOffline(String workerId);

    /**
     * 更新 Worker 主机名
     * @param id Worker ID
     * @param host 新的主机名
     */
    void updateHostById(String id, String host);

    /**
     * 删除 Worker
     * 同步删除关联的 AKSK 和相关任务
     * @param id Worker ID
     */
    void deleteWorker(String id);

    /**
     * 获取在线 Worker 数量
     * @return 在线 Worker 数量
     */
    long countOnlineWorkers();

    /**
     * 根据标签列表查找在线 Worker
     * 匹配规则：Worker 必须拥有所有要求的标签
     * @param requiredTags 任务需要的标签列表
     * @return 符合条件的在线 Worker 列表
     */
    List<Worker> findOnlineWorkersByTags(Set<String> requiredTags);

}