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
