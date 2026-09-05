package com.sjdt.worker.handler;

import com.sjdt.worker.client.MinioStorage;
import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.config.WorkerProperties;
import com.sjdt.worker.model.TaskExecuteMessage;
import com.sjdt.worker.service.ProcessManager;
import com.sjdt.worker.service.TaskInstanceManager;
import com.sjdt.worker.service.TaskResultPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Python 脚本处理器（解释器探测：优先 python3，缺失时降级 python，兼容 Linux / Windows）
 */
@Component
public class PythonHandler extends AbstractProcessHandler {

    /** 探测结果缓存（首次使用后固定，避免每个任务都探测一次） */
    private static volatile String interpreter;

    public PythonHandler(WorkerProperties properties,
                         WorkerContext workerContext,
                         MinioStorage minioStorage,
                         ProcessManager processManager,
                         TaskInstanceManager instanceManager,
                         TaskResultPublisher resultPublisher) {
        super(properties, workerContext, minioStorage, processManager, instanceManager, resultPublisher);
    }

    @Override
    public String supportType() {
        return "python";
    }

    @Override
    protected String normalizeFileName(String name) {
        return name.toLowerCase().endsWith(".py") ? name : name + ".py";
    }

    @Override
    protected List<String> buildCommand(TaskExecuteMessage task, ScriptDownload download) {
        return List.of(pythonInterpreter(), download.entryPath());
    }

    /**
     * 返回可用的 Python 解释器命令（进程级缓存）。
     * <p>
     * 探测顺序：优先 {@code python3}；若不可用则降级为 {@code python}。
     * 这样无论 Linux（通常 python3）还是 Windows（通常 python）都能自动选到可用的解释器。
     *
     * @return 解释器命令串
     */
    private String pythonInterpreter() {
        if (interpreter == null) {
            synchronized (PythonHandler.class) {
                if (interpreter == null) {
                    interpreter = detectInterpreter();
                }
            }
        }
        return interpreter;
    }

    /** 依次探测候选解释器，返回第一个可用者；都不可用则回退 python3（由后续启动报错兜底） */
    private static String detectInterpreter() {
        for (String candidate : new String[]{"python3", "python"}) {
            if (executable(candidate)) {
                return candidate;
            }
        }
        return "python3";
    }

    /** 尝试执行 {cmd} --version，能正常返回退出码 0 视为可用 */
    private static boolean executable(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "--version").start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (IOException e) {
            // 命令不存在（Windows 下进程启动文件找不到）视为不可用
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
