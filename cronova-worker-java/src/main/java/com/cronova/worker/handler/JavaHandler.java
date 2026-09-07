package com.cronova.worker.handler;

import com.cronova.worker.client.MinioStorage;
import com.cronova.worker.common.WorkerContext;
import com.cronova.worker.config.WorkerProperties;
import com.cronova.worker.model.TaskExecuteMessage;
import com.cronova.worker.service.ProcessManager;
import com.cronova.worker.service.TaskInstanceManager;
import com.cronova.worker.service.TaskResultPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Java 脚本处理器（执行 .jar 或 .class 文件）
 * <p>
 * 对应 Python 执行器 java_handler，含 Spring 配置文件自动探测
 * （application*.yml/yaml/properties、bootstrap*）并注入 --spring.config.location。
 */
@Slf4j
@Component
public class JavaHandler extends AbstractProcessHandler {

    public JavaHandler(WorkerProperties properties,
                       WorkerContext workerContext,
                       MinioStorage minioStorage,
                       ProcessManager processManager,
                       TaskInstanceManager instanceManager,
                       TaskResultPublisher resultPublisher) {
        super(properties, workerContext, minioStorage, processManager, instanceManager, resultPublisher);
    }

    @Override
    public String supportType() {
        return "java";
    }

    @Override
    protected List<String> buildCommand(TaskExecuteMessage task, ScriptDownload download) throws Exception {
        String entryPath = download.entryPath();
        String ext = extensionOf(entryPath);

        List<String> cmd;
        if (".jar".equals(ext)) {
            cmd = new ArrayList<>(List.of("java", "-jar", entryPath));
        } else if (".class".equals(ext)) {
            String fileName = Path.of(entryPath).getFileName().toString();
            String className = fileName.substring(0, fileName.length() - ".class".length());
            cmd = new ArrayList<>(List.of("java", "-cp", download.workDir(), className));
        } else {
            throw new IllegalArgumentException("不支持的 Java 文件格式：" + ext);
        }

        // 自动探测并加载 Spring 配置文件
        List<String> configFiles = detectConfigFiles(Path.of(download.workDir()));
        if (!configFiles.isEmpty()) {
            log.info("检测到配置文件：{}，将添加到 JVM 参数中", configFiles);
            String configArg = "--spring.config.location=" + String.join(",", configFiles);
            cmd.add(1, configArg);
        }
        return cmd;
    }

    private String extensionOf(String path) {
        String name = Path.of(path).getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx).toLowerCase();
    }

    private List<String> detectConfigFiles(Path workDir) {
        List<String> configFiles = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(workDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                boolean matched = name.equals("application.yml") || name.equals("application.yaml")
                        || name.equals("application.properties")
                        || name.matches("application[-.][a-zA-Z0-9]+.(yml|yaml|properties)")
                        || name.matches("bootstrap[-.]?[a-zA-Z0-9]*.(yml|yaml|properties)");
                if (matched) {
                    configFiles.add("file:" + p.toAbsolutePath());
                }
            });
        } catch (IOException e) {
            log.warn("检测配置文件时发生异常：{}", e.getMessage());
        }
        return configFiles;
    }
}
