package com.sjdt.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.enums.ScriptType;
import com.sjdt.scheduler.domain.vo.DashboardVO;
import com.sjdt.scheduler.domain.vo.TaskVO;
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
        
        // 6. 获取最近任务执行记录（需要转换格式）
        List<TaskRun> recentRuns = taskRunService.listRecentRuns(10);
        List<TaskVO> recentTasks = recentRuns.stream()
            .map(this::convertTaskRunToTaskVO)
            .collect(Collectors.toList());
        vo.setRecentTasks(recentTasks);
        
        // 7. 生成图表数据（最近7天）
        List<DashboardVO.ChartData> chartData = generateChartData();
        vo.setChartData(chartData);
        
        log.info("仪表盘数据统计完成，总任务: {}, 总执行: {}, 成功率: {}%", 
                stats.getTotalTasks(), stats.getTotalRuns(), stats.getSuccessRate());
        
        return Result.success(vo);
    }

    /**
     * 将TaskRun转换为TaskVO格式，用于前端显示
     */
    private TaskVO convertTaskRunToTaskVO(TaskRun taskRun) {
        TaskVO taskVO = new TaskVO();
        // 这里简化处理，实际应该关联查询任务信息
        taskVO.setId(taskRun.getId());
        taskVO.setName("任务执行#" + taskRun.getId().substring(0, 8));
        taskVO.setScriptType(ScriptType.valueOf("python")); // 默认类型
        taskVO.setCreateTime(taskRun.getCreateTime());
        return taskVO;
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
}
