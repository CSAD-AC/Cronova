package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.service.ITaskService;
import org.springframework.stereotype.Service;

/**
 * Task Service 实现类
 * 提供 Task 任务的管理和操作的具体实现
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

    /**
     * 根据 Worker ID 更新任务的 target_worker 为 null
     * @param workerId Worker ID
     */
    @Override
    public void updateTargetWorkerToNullByWorkerId(String workerId) {
        this.lambdaUpdate()
                .eq(Task::getTargetWorker, workerId)
                .set(Task::getTargetWorker, null)
                .update();
    }

}