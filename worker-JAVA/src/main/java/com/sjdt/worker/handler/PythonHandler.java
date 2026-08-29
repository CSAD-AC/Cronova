package com.sjdt.worker.handler;

import com.sjdt.worker.client.MinioStorage;
import com.sjdt.worker.common.WorkerContext;
import com.sjdt.worker.config.WorkerProperties;
import com.sjdt.worker.model.TaskExecuteMessage;
import com.sjdt.worker.service.ProcessManager;
import com.sjdt.worker.service.TaskInstanceManager;
import com.sjdt.worker.service.TaskResultPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Python 脚本处理器（python3 解释器执行）
 */
@Component
public class PythonHandler extends AbstractProcessHandler {

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
        return List.of("python3", download.entryPath());
    }
}
