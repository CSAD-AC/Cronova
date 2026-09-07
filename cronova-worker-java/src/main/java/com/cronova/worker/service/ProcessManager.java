package com.cronova.worker.service;

import com.cronova.worker.model.TaskExecuteMessage;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 进程管理器：登记运行中的任务进程，支持暂停 / 恢复 / 终止。
 * <p>
 * 与 Python 执行器 process_manager 对齐：
 * <ul>
 *   <li>暂停/恢复：向进程组发送 SIGSTOP / SIGCONT（子进程经 setsid 建独立会话，pgid=pid）；</li>
 *   <li>终止：先 SIGTERM（destroy），等待 5s 后未退出则 SIGKILL（destroyForcibly）；</li>
 *   <li>force=true 直接 SIGKILL（超时强制停止场景）。</li>
 * </ul>
 */
@Slf4j
@Component
public class ProcessManager {

    private final Map<String, RunningProcess> processes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pausedFlags = new ConcurrentHashMap<>();

    public void add(String taskRunId, RunningProcess running) {
        processes.put(taskRunId, running);
        pausedFlags.put(taskRunId, false);
        log.info("进程已登记，taskRunId={}, pid={}", taskRunId, running.pid);
    }

    public RunningProcess get(String taskRunId) {
        return processes.get(taskRunId);
    }

    public RunningProcess remove(String taskRunId) {
        pausedFlags.remove(taskRunId);
        return processes.remove(taskRunId);
    }

    public boolean isRunning(String taskRunId) {
        return processes.containsKey(taskRunId);
    }

    public boolean isPaused(String taskRunId) {
        return Boolean.TRUE.equals(pausedFlags.getOrDefault(taskRunId, false));
    }

    public int runningCount() {
        return processes.size();
    }

    public List<RunningProcess> getAll() {
        return new ArrayList<>(processes.values());
    }

    public boolean stop(String taskRunId, boolean force) {
        RunningProcess running = processes.get(taskRunId);
        if (running == null || !running.process.isAlive()) {
            log.warn("任务进程不存在或已停止，taskRunId={}", taskRunId);
            return false;
        }
        try {
            if (force) {
                running.process.destroyForcibly();
                running.process.waitFor(5, TimeUnit.SECONDS);
                log.info("强制终止进程(SIGKILL)，taskRunId={}, pid={}", taskRunId, running.pid);
            } else {
                running.process.destroy();
                boolean exited = running.process.waitFor(5, TimeUnit.SECONDS);
                if (!exited) {
                    running.process.destroyForcibly();
                    log.warn("进程未在 5s 内退出，强制终止，taskRunId={}, pid={}", taskRunId, running.pid);
                } else {
                    log.info("正常终止进程(SIGTERM)，taskRunId={}, pid={}", taskRunId, running.pid);
                }
            }
            remove(taskRunId);
            return true;
        } catch (Exception e) {
            log.error("停止任务进程失败，taskRunId={}, err={}", taskRunId, e.getMessage());
            return false;
        }
    }

    public boolean pause(String taskRunId) {
        RunningProcess running = processes.get(taskRunId);
        if (running == null || !running.process.isAlive()) {
            log.warn("任务进程不存在或已停止，无法暂停，taskRunId={}", taskRunId);
            return false;
        }
        if (isPaused(taskRunId)) {
            log.info("任务已处于暂停状态，跳过，taskRunId={}", taskRunId);
            return true;
        }
        try {
            signalGroup(running.pid, "STOP");
            pausedFlags.put(taskRunId, true);
            log.info("任务进程已暂停，taskRunId={}, pid={}", taskRunId, running.pid);
            return true;
        } catch (Exception e) {
            log.error("暂停任务进程失败，taskRunId={}, err={}", taskRunId, e.getMessage());
            return false;
        }
    }

    public boolean resume(String taskRunId) {
        RunningProcess running = processes.get(taskRunId);
        if (running == null || !running.process.isAlive()) {
            log.warn("任务进程不存在或已停止，无法恢复，taskRunId={}", taskRunId);
            return false;
        }
        if (!isPaused(taskRunId)) {
            log.info("任务未处于暂停状态，跳过恢复，taskRunId={}", taskRunId);
            return true;
        }
        try {
            signalGroup(running.pid, "CONT");
            pausedFlags.put(taskRunId, false);
            log.info("任务进程已恢复，taskRunId={}, pid={}", taskRunId, running.pid);
            return true;
        } catch (Exception e) {
            log.error("恢复任务进程失败，taskRunId={}, err={}", taskRunId, e.getMessage());
            return false;
        }
    }

    /**
     * 向进程组发送信号（子进程经 setsid 启动，pgid == pid）。
     * 失败时回退为向单个进程发送信号。
     */
    private void signalGroup(long pid, String signal) {
        if (isWindows()) {
            log.warn("Windows 环境不支持进程组信号，跳过 {}", signal);
            return;
        }
        String[] commands = {"kill", "-" + signal, "-" + pid};
        try {
            Process p = new ProcessBuilder(commands).redirectErrorStream(true).start();
            p.waitFor(5, TimeUnit.SECONDS);
            if (p.exitValue() != 0) {
                // 回退：进程组不存在，向单进程发送
                new ProcessBuilder("kill", "-" + signal, String.valueOf(pid))
                        .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("发送信号 {} 失败，pid={}, err={}", signal, pid, e.getMessage());
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** 优雅关闭所有运行中的任务 */
    public void shutdownAll() {
        List<String> keys = new ArrayList<>(processes.keySet());
        for (String key : keys) {
            log.info("退出时终止任务，taskRunId={}", key);
            stop(key, false);
        }
    }

    /** 运行中的任务进程信息 */
    @Getter
    public static class RunningProcess {
        private final Process process;
        private final long pid;
        private final TaskExecuteMessage task;
        private final Instant startTime;
        private final String logBucket;
        private final String logObject;
        private final String tempPath;

        public RunningProcess(Process process, long pid, TaskExecuteMessage task,
                              Instant startTime, String logBucket, String logObject, String tempPath) {
            this.process = process;
            this.pid = pid;
            this.task = task;
            this.startTime = startTime;
            this.logBucket = logBucket;
            this.logObject = logObject;
            this.tempPath = tempPath;
        }
    }
}
