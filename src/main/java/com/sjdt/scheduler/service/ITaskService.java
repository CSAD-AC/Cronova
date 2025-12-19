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

}