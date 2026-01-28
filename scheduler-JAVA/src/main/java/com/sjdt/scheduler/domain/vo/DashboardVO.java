package com.sjdt.scheduler.domain.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DashboardVO {
    private Stats stats;
    private List<TaskRunVO> recentTasks;
    private List<ChartData> chartData;
    private List<TaskTypeData> taskTypeDistribution;

    @Data
    public static class Stats {
        private long totalTasks;         // 总任务数
        private long runningTasks;       // 运行中任务数
        private long todayExecutions;    // 今日执行次数
        private long totalRuns;          // 总执行次数
        private long successRuns;        // 成功执行次数
        private long failureRuns;        // 失败执行次数
        private long activeWorkers;      // 在线执行器数
        private long totalWorkers;       // 总执行器数
        private double successRate;      // 执行成功率
    }

    @Data
    public static class ChartData {
        private String date;
        private long success;
        private long failure;
    }

    @Data
    public static class TaskTypeData {
        private String type;
        private String label;
        private long count;
        private double percentage;
    }
}
