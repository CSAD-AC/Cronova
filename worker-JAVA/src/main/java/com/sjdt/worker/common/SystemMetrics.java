package com.sjdt.worker.common;

import com.sjdt.worker.service.ProcessManager;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

/**
 * 系统指标采集（CPU / 内存 / 运行任务数）
 * <p>
 * 对应 Python 执行器的 SystemUtils（psutil）。
 */
@Component
public class SystemMetrics {

    private final ProcessManager processManager;

    public SystemMetrics(ProcessManager processManager) {
        this.processManager = processManager;
    }

    /** CPU 使用率（0~1，乘以 100 为百分比；不可用时返回 0） */
    public double getCpuUsagePercent() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            double load = os.getSystemCpuLoad();
            if (load < 0) {
                return 0.0;
            }
            return Math.round(load * 10000) / 100.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** 内存使用率（百分比） */
    public double getMemUsagePercent() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            long total = os.getTotalMemorySize();
            long free = os.getFreeMemorySize();
            if (total <= 0) {
                return 0.0;
            }
            double used = (double) (total - free) / total * 100;
            return Math.round(used * 100) / 100.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /** 当前运行中的任务数（取自进程注册表，真实计数） */
    public int getRunningTasks() {
        return processManager.runningCount();
    }
}
