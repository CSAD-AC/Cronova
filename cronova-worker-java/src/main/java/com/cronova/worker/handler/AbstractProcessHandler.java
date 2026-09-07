package com.cronova.worker.handler;

import com.cronova.worker.client.MinioStorage;
import com.cronova.worker.common.WorkerContext;
import com.cronova.worker.config.WorkerProperties;
import com.cronova.worker.model.TaskExecuteMessage;
import com.cronova.worker.model.TaskResultMessage;
import com.cronova.worker.service.ProcessManager;
import com.cronova.worker.service.TaskInstanceManager;
import com.cronova.worker.service.Cfl;
import com.cronova.worker.service.TaskLogPipeline;
import com.cronova.worker.service.TaskResultPublisher;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 进程型任务处理器抽象基类（模板方法）
 * <p>
 * 统一流程：创建实例 → 依赖验证 → 下载脚本 → 置 running → 启动进程 →
 * 日志流式上传 MinIO → 汇总结果并发布 TASK_RESULT → 清理。
 * 子类仅需提供：支持类型、入口文件命名、执行命令构造。
 */
@Slf4j
public abstract class AbstractProcessHandler implements TaskHandler {

    protected final WorkerProperties properties;
    protected final WorkerContext workerContext;
    protected final MinioStorage minioStorage;
    protected final ProcessManager processManager;
    protected final TaskInstanceManager instanceManager;
    protected final TaskResultPublisher resultPublisher;

    /** 任务执行线程池（守护线程，异步执行任务，提交后立即返回） */
    private final ExecutorService executor;

    protected AbstractProcessHandler(WorkerProperties properties,
                                     WorkerContext workerContext,
                                     MinioStorage minioStorage,
                                     ProcessManager processManager,
                                     TaskInstanceManager instanceManager,
                                     TaskResultPublisher resultPublisher) {
        this.properties = properties;
        this.workerContext = workerContext;
        this.minioStorage = minioStorage;
        this.processManager = processManager;
        this.instanceManager = instanceManager;
        this.resultPublisher = resultPublisher;
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, properties.getExecutorThreads()), threadFactory("WorkerHandler"));
    }

    private ThreadFactory threadFactory(String prefix) {
        return new ThreadFactory() {
            private int seq = 0;

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + (++seq));
                t.setDaemon(true);
                return t;
            }
        };
    }

    /** 单文件是否需要可执行权限（go 二进制） */
    protected boolean executableFile() {
        return false;
    }

    /** 单文件扩展名规范化（python 补充 .py） */
    protected String normalizeFileName(String name) {
        return name;
    }

    /** 构造执行命令 */
    protected abstract List<String> buildCommand(TaskExecuteMessage task, ScriptDownload download) throws Exception;

    @Override
    public boolean handle(TaskExecuteMessage task) {
        try {
            instanceManager.create(task.getTaskRunId(), task.getTaskId(),
                    workerContext.getWorkerId(), task.getScriptType());
            executor.submit(() -> executeTask(task));
            return true;
        } catch (Exception e) {
            log.error("{} 提交任务失败（{}）：{}", supportType(), task.getTaskId(), e.getMessage(), e);
            instanceManager.updateStatus(task.getTaskRunId(), "failed",
                    Map.of("error_message", e.getMessage()));
            return false;
        }
    }

    private void executeTask(TaskExecuteMessage task) {
        Instant startTime = Instant.now();
        String runId = task.getTaskRunId();
        ScriptDownload download = null;
        Path localLog = null;
        try {
            log.info("开始执行{}脚本，taskId={}, taskRunId={}", supportType(), task.getTaskId(), runId);

            DependencyValidator.validate(task, workerContext);

            download = downloadScript(task);

            String logBucket = properties.getMinio().getBucket();
            String logObject = buildLogObject(task, runId);

            instanceManager.updateStatus(runId, "running",
                    Map.of("log_bucket", logBucket, "log_object", logObject));

            List<String> command = buildCommand(task, download);
            log.info("执行命令：{}，超时时间：{}秒", command, task.timeoutSecondsOrDefault());

            ProcessBuilder pb = new ProcessBuilder(wrapWithSetsid(command));
            // 注入分片身份到子进程环境变量，与 Python 执行器的 shard_env() 对齐；
            // 业务脚本据此只处理归属当前片的记录（hash(主键) % CRONOVA_SHARD_TOTAL == CRONOVA_SHARD_INDEX）
            pb.environment().put("CRONOVA_SHARD_TOTAL", String.valueOf(shardTotal(task)));
            pb.environment().put("CRONOVA_SHARD_INDEX", String.valueOf(shardIndex(task)));
            pb.environment().put("CRONOVA_SHARD_BATCH_ID", task.getShardBatchId() != null ? task.getShardBatchId() : "");
            // 强制 Python 子进程以 UTF-8 输出，避免 Windows 下 GBK 编码被按 UTF-8 误读导致日志乱码
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");
            pb.directory(Path.of(download.workDir()).toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            long pid = process.pid();
            log.info("子进程已启动，taskRunId={}, pid={}", runId, pid);

            processManager.add(runId, new ProcessManager.RunningProcess(
                    process, pid, task, startTime, logBucket, logObject, download.entryPath()));

            localLog = Path.of(download.workDir(), runId + ".log");
            StreamResult stream = streamAndWait(process, task, runId, localLog, logBucket, logObject);

            finishResult(task, startTime, logBucket, logObject, stream);
        } catch (Exception e) {
            log.error("{}任务执行失败（{}）：{}", supportType(), task.getTaskId(), e.getMessage(), e);
            handleError(task, startTime, e.getMessage());
        } finally {
            if (download != null) {
                cleanup(download);
            }
        }
    }

    /** 下载脚本并确定入口文件（每任务独立子目录，避免并发冲突） */
    protected ScriptDownload downloadScript(TaskExecuteMessage task) throws Exception {
        TaskExecuteMessage.ScriptInfo script = task.getScript();
        if (script == null || script.getPath() == null || script.getPath().isBlank()) {
            throw new IllegalStateException("脚本信息缺失");
        }

        String bucket = properties.getMinio().getBucket();
        String object = script.getPath();
        // 兼容 minio://bucket/object 前缀
        if (object.startsWith("minio://")) {
            String rest = object.substring("minio://".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                bucket = rest.substring(0, slash);
                object = rest.substring(slash + 1);
            } else {
                object = rest;
            }
        }
        // 兼容 path 自带 bucket 前缀（如 task-engine/scripts/xxx.py）：剥离首段 bucket，
        // 与目录分支及 Python minio_utils 的解析一致，避免下载时对象名多包一层 bucket 导致 key 不存在
        if (object.startsWith(bucket + "/")) {
            object = object.substring(bucket.length() + 1);
        }

        String rootPath = script.getRootPath();
        boolean isDirectory = Boolean.TRUE.equals(script.getIsDirectory());
        String parentId = script.getParentId();
        String mainFile = script.getMainFile();

        boolean needDownloadDir = isDirectory || parentId != null;
        Path scriptsDir = Path.of(properties.getScriptsDir());

        if (needDownloadDir) {
            // 目录下载：确定前缀
            String prefix;
            if (isDirectory) {
                prefix = object;
            } else {
                prefix = (rootPath != null && !rootPath.isBlank()) ? rootPath
                        : parentPath(object) + "/";
            }
            if (prefix.startsWith(bucket + "/")) {
                prefix = prefix.substring(bucket.length() + 1);
            }
            String dirName = prefix.replaceAll("/+$", "");
            dirName = dirName.isEmpty() ? "root" : dirName.substring(dirName.lastIndexOf('/') + 1);
            Path workDir = scriptsDir.resolve(task.getTaskRunId() + "-" + dirName);

            log.info("下载目录，bucket={}, prefix={}, localDir={}", bucket, prefix, workDir);
            minioStorage.downloadDirectory(bucket, prefix, workDir);

            String entryFile;
            if (mainFile != null && !mainFile.isBlank()) {
                entryFile = mainFile.startsWith("/") ? mainFile.substring(1) : mainFile;
            } else {
                String rel = object.startsWith(bucket + "/") ? object.substring(bucket.length() + 1) : object;
                if (rel.startsWith(prefix)) {
                    rel = rel.substring(prefix.length());
                }
                entryFile = rel;
            }
            Path entryPath = workDir.resolve(entryFile).normalize();
            if (executableFile()) {
                chmodExecutable(entryPath);
            }
            if (!Files.exists(entryPath)) {
                throw new IllegalStateException("入口文件不存在：" + entryPath);
            }
            return new ScriptDownload(entryPath.toString(), workDir.toString());
        }

        // 单文件下载
        Path workDir = scriptsDir.resolve(task.getTaskRunId());
        Files.createDirectories(workDir);
        String name = script.getName() == null || script.getName().isBlank() ? "script" : script.getName();
        name = normalizeFileName(name);
        Path entryPath = workDir.resolve(name).normalize();
        byte[] content = minioStorage.download(bucket, object);
        Files.write(entryPath, content);
        if (executableFile()) {
            chmodExecutable(entryPath);
        }
        return new ScriptDownload(entryPath.toString(), workDir.toString());
    }

    private String parentPath(String object) {
        int idx = object.lastIndexOf('/');
        return idx > 0 ? object.substring(0, idx) : "";
    }

    private void chmodExecutable(Path path) throws Exception {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException e) {
            log.warn("当前文件系统不支持 POSIX 权限设置，跳过：{}", path);
        }
    }

    /** 非 Windows 环境用 setsid 建独立会话（进程组 pgid=pid，供 SIGSTOP/SIGCONT 整组暂停） */
    private List<String> wrapWithSetsid(List<String> command) {
        if (isWindows()) {
            return command;
        }
        List<String> wrapped = new ArrayList<>(command.size() + 1);
        wrapped.add("setsid");
        wrapped.addAll(command);
        return wrapped;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** 分片总数：未设置分片时按 1（单分片），避免注入 "null" 破坏脚本解析 */
    private int shardTotal(TaskExecuteMessage task) {
        return task.getShardTotal() != null ? task.getShardTotal() : 1;
    }

    /** 分片序号：未设置时按 0 */
    private int shardIndex(TaskExecuteMessage task) {
        return task.getShardIndex() != null ? task.getShardIndex() : 0;
    }

    /** 构造任务日志对象名（含分片维度，方案 4.5）；非分片任务为 `task-logs/{taskId}/{runId}.log` */
    private static String buildLogObject(TaskExecuteMessage task, String runId) {
        Integer shardTotal = task.getShardTotal();
        if (shardTotal != null && shardTotal > 1) {
            String batchId = task.getShardBatchId() != null ? task.getShardBatchId() : "batch";
            Integer shardIndex = task.getShardIndex() != null ? task.getShardIndex() : 0;
            return "task-logs/" + task.getTaskId() + "/" + batchId + "/" + shardIndex + "-" + runId + ".log";
        }
        return "task-logs/" + task.getTaskId() + "/" + runId + ".log";
    }

    /** 流式读取 stdout（镜像到容器日志 + 本地文件），CFL 归一并周期增量追加 MinIO（log + .idx），等待进程退出 */
    private StreamResult streamAndWait(Process process, TaskExecuteMessage task, String runId,
                                       Path localLog, String logBucket, String logObject) throws Exception {
        boolean normalize = properties.getLogPipeline().isNormalizeLines();
        // 尾部环形缓冲：只保留最近 N 行（配置）
        TaskLogPipeline pipeline = new TaskLogPipeline(properties.getLogPipeline().getErrorTailLines());
        // CFL 归一化器（shard 字段可选；非分片任务省略）
        Cfl cfl = new Cfl(task.getShardIndex(), task.getShardTotal());
        Path localIdx = Path.of(localLog.toString() + ".idx");

        long start = System.currentTimeMillis();
        long lastUpload = start;
        // 崩溃恢复：远端对象大小 = 已上传字节数（本地文件是其前缀）
        long uploadedOffset = 0;
        long idxOffset = 0;
        try {
            long size = minioStorage.stat(logBucket, logObject);
            if (size > 0) {
                uploadedOffset = size;
            }
            long idxSize = minioStorage.stat(logBucket, logObject + ".idx");
            if (idxSize > 0) {
                idxOffset = idxSize;
            }
        } catch (Exception ignore) {
            // stat 失败（如 MinIO 暂不可用）不阻断任务，从 0 开始
        }
        pipeline.setUploadedOffset(uploadedOffset);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (normalize) {
                    // CFL 归一化：新条目重写头行，续行原样落盘，并产出 .idx 记录
                    Cfl.Entry entry = cfl.process(line, System.currentTimeMillis());
                    if (entry != null) {
                        String header = cfl.formatHeader(entry) + "\n";
                        long offsetBefore = Files.exists(localLog) ? Files.size(localLog) : 0;
                        appendLocalLog(localLog, cfl.formatHeader(entry));
                        appendIdx(localIdx, entry.tsMillis, offsetBefore, entry.levelAbbr());
                        pipeline.add(header);
                        System.out.println(header.stripTrailing());
                    } else {
                        appendLocalLog(localLog, line);
                        pipeline.add(line + "\n");
                        System.out.println(line);
                    }
                } else {
                    // 透传原始输出（灰度关闭 CFL）
                    appendLocalLog(localLog, line);
                    pipeline.add(line + "\n");
                    System.out.println(line);
                }

                long now = System.currentTimeMillis();
                long elapsedSec = (now - start) / 1000;
                long intervalMs = elapsedSec < 10 ? 1000 : (elapsedSec < 60 ? 3000 : 5000);
                if (now - lastUpload >= intervalMs) {
                    long newOffset = appendLog(localLog, logBucket, logObject, pipeline.getUploadedOffset());
                    if (newOffset >= 0) {
                        pipeline.setUploadedOffset(newOffset);
                    }
                    if (normalize) {
                        idxOffset = appendLog(localIdx, logBucket, logObject + ".idx", idxOffset);
                    }
                    lastUpload = now;
                }
            }
        }

        int exitCode = process.waitFor();
        log.info("进程已退出，taskRunId={}, exitCode={}", runId, exitCode);

        // 最终日志追加，仅追加成功才清理本地缓存（失败保留，避免日志丢失）
        if (Files.exists(localLog)) {
            long newOffset = appendLog(localLog, logBucket, logObject, pipeline.getUploadedOffset());
            if (newOffset >= 0) {
                Files.deleteIfExists(localLog);
            }
        }
        if (normalize && Files.exists(localIdx)) {
            idxOffset = appendLog(localIdx, logBucket, logObject + ".idx", idxOffset);
            Files.deleteIfExists(localIdx);
        }
        // StreamResult 只携带尾部日志（已截断）
        return new StreamResult(exitCode, pipeline.errorTail(1000));
    }

    private void appendLocalLog(Path localLog, String line) {
        try {
            Files.createDirectories(localLog.getParent());
            Files.writeString(localLog, line + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("写入本地日志失败：{}", e.getMessage());
        }
    }

    /** 追加 .idx 记录（JSONL：{"t":毫秒,"o":字节偏移,"l":级别缩写}） */
    private void appendIdx(Path localIdx, long tsMillis, long offset, String levelAbbr) {
        try {
            Files.createDirectories(localIdx.getParent());
            String rec = "{\"t\":" + tsMillis + ",\"o\":" + offset + ",\"l\":\"" + levelAbbr + "\"}\n";
            Files.writeString(localIdx, rec, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("写入本地 .idx 失败：{}", e.getMessage());
        }
    }

    /** 增量追加 [uploadedOffset, 文件末尾) 的新增字节；成功返回新偏移，失败返回 -1（本地文件保留） */
    private long appendLog(Path localLog, String logBucket, String logObject, long uploadedOffset) {
        try (InputStream in = Files.newInputStream(localLog)) {
            long skipped = in.skip(uploadedOffset);
            byte[] data = in.readAllBytes();
            if (data.length > 0) {
                return minioStorage.append(logBucket, logObject, data, uploadedOffset);
            }
            return skipped;
        } catch (Exception e) {
            log.warn("日志追加失败，本地文件保留，obj={}, err={}", logObject, e.getMessage());
            return -1;
        }
    }

    /** 汇总并发布最终结果 */
    private void finishResult(TaskExecuteMessage task, Instant startTime,
                              String logBucket, String logObject, StreamResult stream) {
        String runId = task.getTaskRunId();
        Instant endTime = Instant.now();
        long durationMs = Duration.between(startTime, endTime).toMillis();
        int exitCode = stream.exitCode;
        String status = exitCode == 0 ? "success" : "failed";
        String errorMessage = null;
        if (exitCode != 0) {
            String out = stream.output == null ? "" : stream.output.trim();
            errorMessage = out.isEmpty() ? "退出码：" + exitCode : truncate(out, 1000);
        }

        TaskResultMessage result = TaskResultMessage.builder()
                .workerId(workerContext.getWorkerId())
                .taskId(task.getTaskId())
                .taskRunId(runId)
                .status(status)
                .exitCode(exitCode)
                .startTime(formatUtc(startTime))
                .endTime(formatUtc(endTime))
                .durationMs(durationMs)
                .errorMessage(errorMessage)
                .logPath(logBucket + "/" + logObject)
                .shardIndex(task.getShardIndex())
                .shardTotal(task.getShardTotal())
                .shardBatchId(task.getShardBatchId())
                .build();

        resultPublisher.publish(result);

        instanceManager.updateStatus(runId, status, Map.of(
                "exit_code", exitCode,
                "duration_ms", durationMs,
                "error_message", errorMessage == null ? "" : errorMessage));

        processManager.remove(runId);
        log.info("任务执行完成，taskRunId={}, status={}, duration={}ms", runId, status, durationMs);
    }

    /** 处理任务执行异常（解析/下载/启动阶段） */
    private void handleError(TaskExecuteMessage task, Instant startTime, String errorMessage) {
        String runId = task.getTaskRunId();
        try {
            Instant endTime = Instant.now();
            long durationMs = Duration.between(startTime, endTime).toMillis();

            TaskResultMessage result = TaskResultMessage.builder()
                    .workerId(workerContext.getWorkerId())
                    .taskId(task.getTaskId())
                    .taskRunId(runId)
                    .status("failed")
                    .exitCode(1)
                    .startTime(formatUtc(startTime))
                    .endTime(formatUtc(endTime))
                    .durationMs(durationMs)
                    .errorMessage(truncate(errorMessage, 1000))
                    .build();

            resultPublisher.publish(result);
            instanceManager.updateStatus(runId, "failed", Map.of(
                    "exit_code", 1,
                    "duration_ms", durationMs,
                    "error_message", errorMessage));
        } catch (Exception e) {
            log.error("处理任务错误时发生异常，taskRunId={}, err={}", runId, e.getMessage());
        } finally {
            processManager.remove(runId);
        }
    }

    private void cleanup(ScriptDownload download) {
        try {
            Path dir = Path.of(download.workDir());
            deleteRecursively(dir);
        } catch (Exception e) {
            log.warn("清理临时目录失败：{}", e.getMessage());
        }
    }

    private void deleteRecursively(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 忽略清理失败
                }
            });
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private String formatUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /** 脚本下载结果 */
    protected record ScriptDownload(String entryPath, String workDir) {
    }

    private record StreamResult(int exitCode, String output) {
    }
}
