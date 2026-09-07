package com.cronova.scheduler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cronova.scheduler.domain.po.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    default int clearTargetWorkerByWorkerId(String workerId) {
        return this.update(null,
                new LambdaUpdateWrapper<Task>()
                        .eq(Task::getTargetWorker, workerId)
                        .set(Task::getTargetWorker, null)
        );
    }
}
