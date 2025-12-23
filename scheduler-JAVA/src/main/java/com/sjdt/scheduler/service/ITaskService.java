package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.domain.po.Task;

/**
 * Task Service 接口
 * 提供 Task 任务的管理和操作
 */
public interface ITaskService extends IService<Task> {

    /**
     * 根据 Worker ID 更新任务的 target_worker 为 null
     * @param workerId Worker ID
     */
    void updateTargetWorkerToNullByWorkerId(String workerId);

    /**
     * 发布任务
     * 根据任务类型（公共/定向）发送到RocketMQ
     *
     * @param taskId 任务ID
     */
    void publishTask(String taskId);

    /**
     * 发布立即执行的任务
     * 用于一次性任务的立即执行
     *
     * @param task 任务对象
     */
    void publishImmediateTask(Task task);
}