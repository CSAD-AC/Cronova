package com.sjdt.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.po.CallbackJobRun;
import com.sjdt.scheduler.domain.vo.DashboardVO;
import com.sjdt.scheduler.domain.vo.TaskRunVO;
import com.sjdt.scheduler.service.ICallbackJobRunService;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.ITaskRunService;
import com.sjdt.scheduler.service.IWorkerService;
import com.sjdt.scheduler.domain.po.TaskRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@SaCheckLogin
@Tag(name = "仪表盘管理", description = "系统运行概览数据")
@Slf4j
public class DashboardController {

    @Autowired
    private ITaskService taskService;
    
    @Autowired
    private IWorkerService workerService;
    
    @Autowired
    private IScriptService scriptService;

    @Autowired
    private ITaskRunService taskRunService;
    
    @Autowired
    private ICallbackJobRunService callbackJobRunService;

    @GetMapping("/stats")
    @Operation(summary = "获取系统概览统计")
    public Result<DashboardVO> getStats() {
        DashboardVO vo = new DashboardVO();
        
        DashboardVO.Stats stats = new DashboardVO.Stats();
        
        // 1. 任务相关统计
        stats.setTotalTasks(taskService.count());
        
        // 2. 任务执行统计
        ITaskRunService.TaskRunStats taskRunStats = taskRunService.countByStatusAndTimeRange(
            LocalDateTime.now().minusDays(30), LocalDateTime.now()
        );
        
        stats.setTotalRuns(taskRunStats.getTotal());
        stats.setSuccessRuns(taskRunStats.getSuccess());
        stats.setFailureRuns(taskRunStats.getFailure());
        
        // 3. Worker相关统计
        stats.setActiveWorkers(workerService.countOnlineWorkers());
        stats.setTotalWorkers(workerService.count());
        
        // 4. 实时统计
        stats.setRunningTasks(taskRunService.countRunningTasks());
        stats.setTodayExecutions(taskRunService.countTodayExecutions());
        
        // 5. 计算成功率
        if (taskRunStats.getTotal() > 0) {
            stats.setSuccessRate((double) taskRunStats.getSuccess() / taskRunStats.getTotal() * 100);
        } else {
            stats.setSuccessRate(0.0);
        }
        
        vo.setStats(stats);
        
        // 6. 获取最近任务执行记录（同时查询task_run和callback_job_run）
        List<TaskRunVO> recentTaskRuns = getRecentTaskRuns(10);
        vo.setRecentTasks(recentTaskRuns);
        
        // 7. 生成图表数据（最近7天）
        List<DashboardVO.ChartData> chartData = generateChartData();
        vo.setChartData(chartData);
        
        // 8. 生成任务类型分布统计
        List<DashboardVO.TaskTypeData> taskTypeDistribution = generateTaskTypeDistribution();
        vo.setTaskTypeDistribution(taskTypeDistribution);
        
        log.info("仪表盘数据统计完成，总任务: {}, 总执行: {}, 成功率: {}%", 
                stats.getTotalTasks(), stats.getTotalRuns(), stats.getSuccessRate());
        
        return Result.success(vo);
    }

    /**
     * 获取最近的任务执行记录
     * 同时查询task_run（脚本执行）和callback_job_run（回调执行）
     * 通过taskId关联查询任务获取真实的类型信息
     */
    private List<TaskRunVO> getRecentTaskRuns(int limit) {
        List<TaskRunVO> result = new ArrayList<>();
        
        try {
            // 1. 查询脚本执行记录（task_run）
            List<TaskRun> scriptRuns = taskRunService.listRecentRuns(limit);
            
            // 2. 查询回调执行记录（callback_job_run）
            LambdaQueryWrapper<CallbackJobRun> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(CallbackJobRun::getCreateTime)
                    .last("limit " + limit);
            List<CallbackJobRun> callbackRuns = callbackJobRunService.list(wrapper);

            
            // 3. 合并两种执行记录
            List<TaskRunVO> allRuns = new ArrayList<>();
            
            // 处理脚本执行记录
            for (TaskRun run : scriptRuns) {
                TaskRunVO vo = convertTaskRunToTaskRunVO(run, "script");
                allRuns.add(vo);
            }
            
            // 处理回调执行记录
            for (com.sjdt.scheduler.domain.po.CallbackJobRun run : callbackRuns) {
                TaskRunVO vo = convertCallbackJobRunToTaskRunVO(run);
                allRuns.add(vo);
            }
            
            // 4. 按创建时间降序排序
            allRuns.sort((a, b) -> {
                LocalDateTime timeA = a.getCreateTime() != null ? a.getCreateTime() : LocalDateTime.MIN;
                LocalDateTime timeB = b.getCreateTime() != null ? b.getCreateTime() : LocalDateTime.MIN;
                return timeB.compareTo(timeA);
            });
            
            // 5. 返回前limit条
            result = allRuns.stream().limit(limit).collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("获取最近任务执行记录失败: {}", e.getMessage(), e);
        }
        
        return result;
    }

    /**
     * 将TaskRun转换为TaskRunVO格式，用于前端显示
     * 需要关联查询Task获取真实的任务类型
     */
    private TaskRunVO convertTaskRunToTaskRunVO(TaskRun taskRun, String sourceType) {
        // 通过taskId查询关联的任务信息，获取真实的类型
        String executeType = sourceType;
        String scriptType = null;
        
        if (taskRun.getTaskId() != null) {
            com.sjdt.scheduler.domain.po.Task task = taskService.getById(taskRun.getTaskId());
            if (task != null) {
                // 任务定义的executeType（script或callback）
                if (task.getExecuteType() != null) {
                    executeType = task.getExecuteType().name();
                }
                if (task.getScriptType() != null) {
                    scriptType = task.getScriptType().name();
                }
            }
        }

        return TaskRunVO.builder()
            .id(taskRun.getId())
            .taskName("任务执行#" + taskRun.getId().substring(0, 8))
            .executeType(executeType)
            .scriptType(scriptType)
            .status(taskRun.getStatus() != null ? taskRun.getStatus().name() : null)
            .duration(taskRun.getDurationMs())
            .startTime(taskRun.getStartTime())
            .endTime(taskRun.getEndTime())
            .createTime(taskRun.getCreateTime())
            .exitCode(taskRun.getExitCode())
            .errorMessage(taskRun.getErrorMessage())
            .build();
    }

    /**
     * 将CallbackJobRun转换为TaskRunVO格式
     */
    private TaskRunVO convertCallbackJobRunToTaskRunVO(CallbackJobRun run) {
        // 通过taskId查询关联的任务信息
        String executeType = "callback";
        String scriptType = null;
        
        if (run.getTaskId() != null) {
            com.sjdt.scheduler.domain.po.Task task = taskService.getById(run.getTaskId());
            if (task != null) {
                executeType = task.getExecuteType() != null ? task.getExecuteType().name() : "callback";
                if (task.getScriptType() != null) {
                    scriptType = task.getScriptType().name();
                }
            }
        }
        
        return TaskRunVO.builder()
            .id(run.getId())
            .taskName("回调执行#" + run.getId().substring(0, 8))
            .executeType(executeType)
            .scriptType(scriptType)
            .status(run.getStatus())
            .duration(run.getResponseTimeMs() != null ? (long) run.getResponseTimeMs() : null)
            .startTime(run.getStartTime())
            .endTime(run.getEndTime())
            .createTime(run.getCreateTime())
            .exitCode(run.getResponseStatusCode())
            .errorMessage(run.getErrorMessage())
            .build();
    }

    /**
     * 生成最近7天的图表数据
     */
    private List<DashboardVO.ChartData> generateChartData() {
        List<DashboardVO.ChartData> chartData = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime startOfDay = date.atStartOfDay();
            LocalDateTime endOfDay = date.atTime(23, 59, 59);
            
            ITaskRunService.TaskRunStats dayStats = taskRunService.countByStatusAndTimeRange(startOfDay, endOfDay);
            
            DashboardVO.ChartData data = new DashboardVO.ChartData();
            data.setDate(date.format(formatter));
            data.setSuccess(dayStats.getSuccess());
            data.setFailure(dayStats.getFailure());
            chartData.add(data);
        }
        
        return chartData;
    }

    /**
     * 生成任务类型分布统计
     */
    private List<DashboardVO.TaskTypeData> generateTaskTypeDistribution() {
        List<DashboardVO.TaskTypeData> taskTypes = new ArrayList<>();
        
        // 统计定时任务和一次性任务
        long cronTasks = taskService.lambdaQuery()
                .eq(com.sjdt.scheduler.domain.po.Task::getTaskType, "cron")
                .eq(com.sjdt.scheduler.domain.po.Task::getStatus, "active")
                .count();
            
        long onceTasks = taskService.lambdaQuery()
                .eq(com.sjdt.scheduler.domain.po.Task::getTaskType, "once")
                .eq(com.sjdt.scheduler.domain.po.Task::getStatus, "active")
                .count();
        
        long totalTasks = cronTasks + onceTasks;
        
        if (totalTasks > 0) {
            // Cron定时任务
            DashboardVO.TaskTypeData cronData = new DashboardVO.TaskTypeData();
            cronData.setType("cron");
            cronData.setLabel("Cron定时任务");
            cronData.setCount(cronTasks);
            cronData.setPercentage((double) cronTasks / totalTasks * 100);
            taskTypes.add(cronData);
            
            // 一次性任务
            DashboardVO.TaskTypeData onceData = new DashboardVO.TaskTypeData();
            onceData.setType("once");
            onceData.setLabel("一次性任务");
            onceData.setCount(onceTasks);
            onceData.setPercentage((double) onceTasks / totalTasks * 100);
            taskTypes.add(onceData);
        } else {
            // 如果没有任务，返回空数据
            DashboardVO.TaskTypeData emptyData = new DashboardVO.TaskTypeData();
            emptyData.setType("none");
            emptyData.setLabel("暂无任务");
            emptyData.setCount(0);
            emptyData.setPercentage(0.0);
            taskTypes.add(emptyData);
        }
        
        return taskTypes;
    }
}
