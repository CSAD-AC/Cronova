package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TaskRun Service 接口
 * 提供任务执行记录的管理和统计操作
 */
public interface ITaskRunService extends IService<TaskRun> {

    /**
     * 统计今日执行次数
     * @return 今日执行次数
     */
    long countTodayExecutions();

    /**
     * 统计指定时间范围内的执行次数
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 执行次数
     */
    long countExecutionsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计指定状态的任务执行次数
     * @param status 执行状态
     * @return 执行次数
     */
    long countByStatus(TaskRunStatus status);

    /**
     * 统计指定时间范围内的执行次数，按状态分类
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 状态统计结果
     */
    TaskRunStats countByStatusAndTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取最近的任务执行记录
     * @param limit 限制数量
     * @return 任务执行记录列表
     */
    List<TaskRun> listRecentRuns(int limit);

    /**
     * 获取运行中的任务数量
     * @return 运行中任务数量
     */
    long countRunningTasks();

    /**
     * 统计任务执行结果
     */
    class TaskRunStats {
        private long total;
        private long success;
        private long failure;
        private long pending;
        private long running;
        private long timeout;

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public long getSuccess() {
            return success;
        }

        public void setSuccess(long success) {
            this.success = success;
        }

        public long getFailure() {
            return failure;
        }

        public void setFailure(long failure) {
            this.failure = failure;
        }

        public long getPending() {
            return pending;
        }

        public void setPending(long pending) {
            this.pending = pending;
        }

        public long getRunning() {
            return running;
        }

        public void setRunning(long running) {
            this.running = running;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }
}