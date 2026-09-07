package com.cronova.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cronova.scheduler.domain.enums.ExecuteType;
import com.cronova.scheduler.domain.enums.TaskRunStatus;
import com.cronova.scheduler.domain.message.TaskExecuteMessage;
import com.cronova.scheduler.domain.po.Task;
import com.cronova.scheduler.domain.po.TaskRun;
import com.cronova.scheduler.mapper.TaskMapper;
import com.cronova.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Worker 离线主动故障转移服务
 * <p>
 * Worker 心跳丢失被确认离线后，把其上正在执行/待执行的脚本任务<b>主动转移</b>到其他在线 Worker，
 * 而不是被动等待派发/执行超时回收（最差可从几十秒降到秒级）。
 * 处理规则：
 * <ol>
 *     <li>捞取离线 Worker 名下所有 <code>running/pending</code> 的 run；</li>
 *     <li>先把原 run 条件更新为 <code>failed</code>（复用 TaskRunFinalizer，防止与迟到的旧结果竞态）；</li>
 *     <li>原 run 仅在<u>条件更新成功</u>时才转移，否则该 run 已被其他路径（超时/结果回流）推进终态，直接跳过；</li>
 *     <li>原 run 若仍有重试预算，则新建一个 run（沿用其分片身份、不换片）、并按照路由策略重新派发到在线 Worker。</li>
 * </ol>
 * 双保险：主动转移（心跳检查 10s 级） + 既有派发/执行超时监控兜底。
 */
@Slf4j
@Service
public class OrphanTaskFailoverService {

    /** 离线转移原因（写入原 run 的 errorMessage，便于审计） */
    private static final String OFFLINE_MSG = "worker offline, failover";

    private final TaskRunMapper taskRunMapper;
    private final TaskMapper taskMapper;
    private final TaskRunFinalizer taskRunFinalizer;
    private final TaskDispatchService taskDispatchService;
    private final ScriptMessageBuilder scriptMessageBuilder;

    @Autowired
    public OrphanTaskFailoverService(TaskRunMapper taskRunMapper,
                                     TaskMapper taskMapper,
                                     TaskRunFinalizer taskRunFinalizer,
                                     TaskDispatchService taskDispatchService,
                                     ScriptMessageBuilder scriptMessageBuilder) {
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.taskRunFinalizer = taskRunFinalizer;
        this.taskDispatchService = taskDispatchService;
        this.scriptMessageBuilder = scriptMessageBuilder;
    }

    /**
     * 转移指定离线 Worker 名下的孤儿 run
     *
     * @param workerId 已确认离线的 Worker ID
     * @return 成功转移（新建新 run）的孤儿数量
     */
    public int transferOrphans(String workerId) {
        List<TaskRun> orphans = taskRunMapper.selectList(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getWorkerId, workerId)
                .in(TaskRun::getStatus, TaskRunStatus.running, TaskRunStatus.pending));

        if (orphans.isEmpty()) {
            return 0;
        }
        log.warn("离线Worker {} 名下发现{}个待转移run", workerId, orphans.size());

        int transferred = 0;
        for (TaskRun run : orphans) {
            try {
                if (transferOne(run)) {
                    transferred++;
                }
            } catch (Exception e) {
                // 单个孤儿转移失败不影响其他孤儿
                log.error("孤儿run转移异常，WorkerID: {}, RunID: {}", workerId, run.getId(), e);
            }
        }
        return transferred;
    }

    /**
     * 转移单个孤儿 run：判终态原 run + 按预算决定是否新建并重派发
     *
     * @param run 孤儿 run
     * @return true=已新建转移 run
     */
    private boolean transferOne(TaskRun run) {
        Task task = taskMapper.selectById(run.getTaskId());
        if (task == null) {
            // 任务已被删除：仅把孤儿 run 判终态，不新建（无重试预算可沿用）
            tryFailOld(run, "task deleted, failover skipped");
            return false;
        }
        // 故障转移只针对走 MQ 派发的脚本任务；callback 直连执行器，不在此转移
        if (task.getExecuteType() != ExecuteType.script) {
            return false;
        }

        int retryNum = retryNumOf(run);
        int maxRetry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        // 原 run 重试预算已耗尽：只判终态，不再新建
        if (maxRetry > 0 && retryNum >= maxRetry) {
            tryFailOld(run, OFFLINE_MSG + ", retry budget exhausted");
            return false;
        }

        // 条件更新判终态；失败说明 run 已被其他路径推进终态（迟到结果/超时），跳过转移
        if (!tryFailOld(run, OFFLINE_MSG)) {
            log.info("孤儿run {} 已被其他路径推进终态，跳过转移", run.getId());
            return false;
        }

        // 新建转移 run，沿用原 run 分片身份（单片不换片），由路由策略重新选在线 Worker
        TaskRun newRun = createFailoverRun(task, run);
        TaskExecuteMessage message = scriptMessageBuilder.buildScriptMessage(task, newRun);
        boolean dispatched = taskDispatchService.dispatch(task, message, 0);

        if (dispatched) {
            log.info("孤儿run转移成功，RunID: {} -> 新RunID: {}，WorkerID: {}", run.getId(), newRun.getId(), newRun.getWorkerId());
        } else {
            log.warn("孤儿run已转移但暂无可用Worker，新RunID: {}（保持pending，交派发超时监控兜底）", newRun.getId());
        }
        return true;
    }

    /**
     * 把原 run 条件更新为 failed（供判断能否转移）
     *
     * @param run     孤儿 run
     * @param message 失败原因
     * @return true=判终态成功（原 run 可被转移）
     */
    private boolean tryFailOld(TaskRun run, String message) {
        TaskRun failed = TaskRun.builder()
                .id(run.getId())
                .workerId(run.getWorkerId())
                .status(TaskRunStatus.failed)
                .endTime(LocalDateTime.now())
                .errorMessage(message)
                .retryNum(retryNumOf(run))
                .lastHeartbeatTime(LocalDateTime.now())
                .build();
        return taskRunFinalizer.tryAdvanceToTerminal(failed);
    }

    /**
     * 创建转移 run：状态 pending、沿用原 run 分片身份、重试次数沿用原值（故障转移不额外消耗重试预算）
     *
     * @param task 任务定义
     * @param run  原孤儿 run
     * @return 新 run
     */
    private TaskRun createFailoverRun(Task task, TaskRun run) {
        TaskRun newRun = TaskRun.builder()
                .taskId(task.getId())
                .status(TaskRunStatus.pending)
                .retryNum(retryNumOf(run))
                .shardIndex(run.getShardIndex() != null ? run.getShardIndex() : 0)
                .shardTotal(run.getShardTotal() != null ? run.getShardTotal() : 1)
                .shardBatchId(run.getShardBatchId())
                .startTime(LocalDateTime.now())
                .createTime(LocalDateTime.now())
                .build();
        taskRunMapper.insert(newRun);
        return newRun;
    }

    /**
     * 读取 run 的重试次数（NULL 视为 0）
     */
    private int retryNumOf(TaskRun run) {
        return run.getRetryNum() == null ? 0 : run.getRetryNum();
    }
}