package com.sjdt.scheduler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import com.sjdt.scheduler.service.ITaskRunService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * TaskRun Service 实现类
 * 提供任务执行记录的管理和统计操作
 */
@Service
@Slf4j
public class TaskRunServiceImpl extends ServiceImpl<TaskRunMapper, TaskRun> implements ITaskRunService {

    @Override
    public long countTodayExecutions() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        
        LambdaQueryWrapper<TaskRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(TaskRun::getCreateTime, startOfDay, endOfDay);
        
        return this.count(wrapper);
    }

    @Override
    public long countExecutionsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<TaskRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(TaskRun::getCreateTime, startTime, endTime);
        
        return this.count(wrapper);
    }

    @Override
    public long countByStatus(TaskRunStatus status) {
        LambdaQueryWrapper<TaskRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskRun::getStatus, status);
        
        return this.count(wrapper);
    }

    @Override
    public ITaskRunService.TaskRunStats countByStatusAndTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        ITaskRunService.TaskRunStats stats = new ITaskRunService.TaskRunStats();
        
        // 统计总执行次数
        stats.setTotal(countExecutionsByTimeRange(startTime, endTime));
        
        // 按状态统计
        stats.setSuccess(countByStatusAndTimeRange(startTime, endTime, TaskRunStatus.success));
        stats.setFailure(countByStatusAndTimeRange(startTime, endTime, TaskRunStatus.failed));
        stats.setPending(countByStatusAndTimeRange(startTime, endTime, TaskRunStatus.pending));
        stats.setRunning(countByStatusAndTimeRange(startTime, endTime, TaskRunStatus.running));
        stats.setTimeout(countByStatusAndTimeRange(startTime, endTime, TaskRunStatus.timeout));
        
        return stats;
    }

    private long countByStatusAndTimeRange(LocalDateTime startTime, LocalDateTime endTime, TaskRunStatus status) {
        LambdaQueryWrapper<TaskRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.between(TaskRun::getCreateTime, startTime, endTime)
              .eq(TaskRun::getStatus, status);
        
        return this.count(wrapper);
    }

    @Override
    public List<TaskRun> listRecentRuns(int limit) {
        LambdaQueryWrapper<TaskRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(TaskRun::getCreateTime)
              .last("limit " + limit);
        
        return this.list(wrapper);
    }

    @Override
    public long countRunningTasks() {
        LambdaQueryWrapper<TaskRun> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskRun::getStatus, TaskRunStatus.running);
        
        return this.count(wrapper);
    }
}