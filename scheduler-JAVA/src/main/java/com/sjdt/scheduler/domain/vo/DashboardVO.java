package com.sjdt.scheduler.domain.vo;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DashboardVO {
    private Stats stats;
    private List<TaskVO> recentTasks;
    private List<ChartData> chartData;

    @Data
    public static class Stats {
        private long totalTasks;
        private long totalRuns;
        private long successRuns;
        private long failureRuns;
        private long activeWorkers;
    }

    @Data
    public static class ChartData {
        private String date;
        private long success;
        private long failure;
    }
}
