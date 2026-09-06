package com.sjdt.scheduler.service;

import com.sjdt.scheduler.domain.enums.TaskType;
import com.sjdt.scheduler.domain.message.TaskExecuteMessage;
import com.sjdt.scheduler.domain.po.Script;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.ScriptMapper;
import com.sjdt.scheduler.service.IScriptService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 脚本任务消息构造器
 * <p>
 * 统一构造发往 Worker 的 {@link TaskExecuteMessage}，供三条派发路径复用：
 * <ol>
 *     <li>cron 定时触发（CronTaskScheduler）；</li>
 *     <li>SERIAL 排队推进（QueuedRunDispatcher）；</li>
 *     <li>Worker 离线故障转移（OrphanTaskFailoverService）。</li>
 * </ol>
 * run 非空时自动携带分片身份（shardIndex/shardTotal/shardBatchId），
 * 保证排队/转移重发沿续原 run 的分片身份（单片不换片）。
 */
@Slf4j
@Service
public class ScriptMessageBuilder {

    /** 脚本Mapper */
    private final ScriptMapper scriptMapper;

    /** 脚本服务（查询父目录路径） */
    private final IScriptService scriptService;

    @Autowired
    public ScriptMessageBuilder(ScriptMapper scriptMapper, IScriptService scriptService) {
        this.scriptMapper = scriptMapper;
        this.scriptService = scriptService;
    }

    /**
     * 构造脚本任务的执行消息
     * <p>
     * 分片派发模板请求（run 为 null）时携带 taskRunId=null，由 dispatchSharded 按片覆写。
     *
     * @param task 任务对象（executeType=script）
     * @param run  任务运行实例；非空则携带其分片身份，为空表示构造分片模板
     * @return 脚本任务执行消息
     */
    public TaskExecuteMessage buildScriptMessage(Task task, TaskRun run) {
        Script script = null;
        if (task.getScriptId() != null && !task.getScriptId().isEmpty()) {
            script = scriptMapper.selectById(task.getScriptId());
        }

        TaskExecuteMessage.ScriptInfo scriptInfo = TaskExecuteMessage.ScriptInfo.builder()
            .id(script != null ? script.getId() : null)
            .name(script != null ? script.getName() : null)
            .path(script != null ? script.getPath() : null)
            .isDirectory(script != null ? script.getIsDirectory() : null)
            .mainFile(script != null ? script.getMainFile() : null)
            .parentId(script != null ? script.getParentId() : null)
            .rootPath(getRootPath(script))
            .build();

        TaskExecuteMessage.TaskExecuteMessageBuilder builder = TaskExecuteMessage.builder()
            .taskId(task.getId())
            .taskName(task.getName())
            .taskRunId(run != null ? run.getId() : null)
            .description(task.getDescription())
            .scriptType(task.getScriptType() != null ? task.getScriptType().name() : null)
            .script(scriptInfo)
            .taskType(TaskType.cron.name())
            .cronExpr(task.getCronExpr())
            .targetWorkerId(task.getTargetWorker())
            .timeoutSeconds(task.getTimeoutSeconds());

        if (run != null) {
            builder.shardIndex(run.getShardIndex() != null ? run.getShardIndex() : 0)
                    .shardTotal(run.getShardTotal() != null ? run.getShardTotal() : 1)
                    .shardBatchId(run.getShardBatchId());
        }

        return builder.build();
    }

    /**
     * 获取脚本的根目录路径
     * 如果脚本有父目录，则返回父目录的路径；否则返回脚本所在的目录路径
     *
     * @param script 脚本对象
     * @return 根目录路径
     */
    private String getRootPath(Script script) {
        if (script == null) {
            return null;
        }

        // 如果是目录类型，根目录就是自己的路径
        if (Boolean.TRUE.equals(script.getIsDirectory())) {
            return script.getPath();
        }

        // 如果有父目录ID，需要查询父目录的路径
        if (script.getParentId() != null) {
            Script parentScript = scriptService.getById(script.getParentId());
            if (parentScript != null) {
                return parentScript.getPath();
            }
        }

        // 否则返回脚本所在的目录路径（去掉文件名部分）
        String scriptPath = script.getPath();
        int lastSlash = scriptPath.lastIndexOf('/');
        if (lastSlash > 0) {
            return scriptPath.substring(0, lastSlash);
        }

        return "/";
    }
}