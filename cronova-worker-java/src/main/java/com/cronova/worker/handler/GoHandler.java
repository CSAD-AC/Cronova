package com.cronova.worker.handler;

import com.cronova.worker.client.MinioStorage;
import com.cronova.worker.common.WorkerContext;
import com.cronova.worker.config.WorkerProperties;
import com.cronova.worker.model.TaskExecuteMessage;
import com.cronova.worker.service.ProcessManager;
import com.cronova.worker.service.TaskInstanceManager;
import com.cronova.worker.service.TaskResultPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Go 处理器（执行已编译的二进制文件，无需 Go 环境）
 */
@Component
public class GoHandler extends AbstractProcessHandler {

    public GoHandler(WorkerProperties properties,
                     WorkerContext workerContext,
                     MinioStorage minioStorage,
                     ProcessManager processManager,
                     TaskInstanceManager instanceManager,
                     TaskResultPublisher resultPublisher) {
        super(properties, workerContext, minioStorage, processManager, instanceManager, resultPublisher);
    }

    @Override
    public String supportType() {
        return "go";
    }

    @Override
    protected boolean executableFile() {
        return true;
    }

    @Override
    protected List<String> buildCommand(TaskExecuteMessage task, ScriptDownload download) {
        return List.of(download.entryPath());
    }
}
