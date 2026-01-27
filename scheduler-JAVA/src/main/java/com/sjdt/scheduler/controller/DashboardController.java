package com.sjdt.scheduler.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.sjdt.scheduler.common.Result;
import com.sjdt.scheduler.domain.vo.DashboardVO;
import com.sjdt.scheduler.domain.vo.TaskVO;
import com.sjdt.scheduler.service.ICallbackJobRunService;
import com.sjdt.scheduler.service.IScriptService;
import com.sjdt.scheduler.service.ITaskService;
import com.sjdt.scheduler.service.IWorkerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

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

    @GetMapping("/stats")
    @Operation(summary = "获取系统概览统计")
    public Result<DashboardVO> getStats() {
        DashboardVO vo = new DashboardVO();
        
        DashboardVO.Stats stats = new DashboardVO.Stats();
        // 这里简化实现，实际应从数据库统计
        stats.setTotalTasks(taskService.count());
        stats.setActiveWorkers(workerService.countOnlineWorkers());
        
        // 模拟一些数据
        stats.setTotalRuns(1250);
        stats.setSuccessRuns(1200);
        stats.setFailureRuns(50);
        
        vo.setStats(stats);
        
        // 获取最近任务
        List<TaskVO> recentTasks = taskService.listRecentTasks(10);
        vo.setRecentTasks(recentTasks);
        
        return Result.success(vo);
    }
}
