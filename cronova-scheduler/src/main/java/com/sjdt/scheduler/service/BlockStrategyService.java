package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.message.TaskStopMessage;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 阻塞策略判定服务
 * <p>
 * 控制"同一任务上一轮还没跑完时，本轮到点该如何处置"，避免同一任务自叠与资源堆积。
 * 作用于<b>作业级（同一 Task）</b>的重叠控制，不限制执行器层面的并发（不同任务并发照跑）。
 * 仅用于脚本任务（分片任务是并发模型，不参与阻塞）。三种策略：
 * <ul>
 *     <li><b>DISCARD_LATER</b>（默认）：上一轮未跑完则本轮记一条 <code>skipped</code> 记录后丢弃；</li>
 *     <li><b>COVER_EARLY</b>：对上一轮 run 发 STOP 后放行本轮（杀旧派新）；</li>
 *     <li><b>SERIAL</b>：上一轮未跑完则本轮先记 <code>queued</code> 排队，
 *         由 QueuedRunDispatcher 在上一个兄弟终态后自动推进派发。</li>
 * </ul>
 * 被丢弃/排队的记录统一按 N=1 特例写入分片身份，与正常 run 走同一结算路径（但两者非终态，
 * 不会触发批次结算，保持语义一致）。
 */
@Slf4j
@Service
public class BlockStrategyService {

    /** 阻塞策略（对应 tasks.block_strategy 字面值） */
    public static final String STRATEGY_SERIAL = "SERIAL";
    public static final String STRATEGY_COVER_EARLY = "COVER_EARLY";
    public static final String STRATEGY_DISCARD_LATER = "DISCARD_LATER";

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;

    /** 任务控制消息生产（COVER_EARLY 停旧） */
    private final TaskProducerService taskProducerService;

    @Autowired
    public BlockStrategyService(TaskRunMapper taskRunMapper, TaskProducerService taskProducerService) {
        this.taskRunMapper = taskRunMapper;
        this.taskProducerService = taskProducerService;
    }

    /**
     * 阻塞策略会对本次触发的影响决策
     */
    public enum Decision {
        /** 上一轮不阻塞，正常派发本轮 */
        PROCEED,
        /** 已建 queued run 排队，本次不派发 */
        QUEUED,
        /** 已建 skipped run 丢弃，本次不派发 */
        SKIPPED
    }

    /**
     * 判定阻塞策略：查询同一任务是否存在进行中/排队中的兄弟 run
     *
     * @param task 任务对象
     * @return 决策结果
     */
    public Decision evaluate(Task task) {
        TaskRun active = findActiveSibling(task.getId());
        if (active == null) {
            return Decision.PROCEED;
        }

        String strategy = task.getBlockStrategy();
        if (strategy == null || strategy.isBlank()) {
            strategy = STRATEGY_DISCARD_LATER;
        }

        return switch (strategy.toUpperCase()) {
            case STRATEGY_SERIAL -> { yield queueRun(task); }
            case STRATEGY_COVER_EARLY -> { yield coverEarly(task, active); }
            default -> { yield discardLater(task); }
        };
    }

    /**
     * 查询同一任务最新一条"进行中或排队中"的 run（上一轮未跑完的证据）
     */
    private TaskRun findActiveSibling(String taskId) {
        return taskRunMapper.selectOne(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getTaskId, taskId)
                .in(TaskRun::getStatus, TaskRunStatus.running,
                        TaskRunStatus.pending, TaskRunStatus.queued)
                .orderByDesc(TaskRun::getCreateTime)
                .last("LIMIT 1"));
    }

    /**
     * DISCARD_LATER：记一条 skipped 记录后丢弃本轮
     */
    private Decision discardLater(Task task) {
        createBlockedRun(task, TaskRunStatus.skipped);
        log.info("阻塞策略DISCARD_LATER：任务{}上一轮未跑完，本轮丢弃（记录skipped）", task.getId());
        return Decision.SKIPPED;
    }

    /**
     * SERIAL：记一条 queued run 排队，等上一个兄弟终态后由推进器派发
     */
    private Decision queueRun(Task task) {
        createBlockedRun(task, TaskRunStatus.queued);
        log.info("阻塞策略SERIAL：任务{}上一轮未跑完，本轮排队（记录queued）", task.getId());
        return Decision.QUEUED;
    }

    /**
     * COVER_EARLY：向上一轮 run 所在 Worker 发 STOP，放行本轮
     */
    private Decision coverEarly(Task task, TaskRun active) {
        if (active.getWorkerId() != null && !active.getWorkerId().isBlank()) {
            TaskStopMessage stopMsg = TaskStopMessage.builder()
                    .taskId(task.getId())
                    .taskRunId(active.getId())
                    .targetWorkerId(active.getWorkerId())
                    .reason("阻塞策略COVER_EARLY：同一任务新一轮触发，停止旧实例")
                    .build();
            taskProducerService.sendTaskControl(stopMsg);
            log.info("阻塞策略COVER_EARLY：任务{}停止旧实例{}后放行本轮", task.getId(), active.getId());
        } else {
            log.info("阻塞策略COVER_EARLY：任务{}旧实例{}无Worker，仅放行本轮", task.getId(), active.getId());
        }
        return Decision.PROCEED;
    }

    /**
     * 创建被阻塞的记录（skipped/queued），统一按 N=1 特例写入分片身份
     */
    private void createBlockedRun(Task task, TaskRunStatus status) {
        TaskRun run = TaskRun.builder()
                .taskId(task.getId())
                .status(status)
                .shardIndex(0)
                .shardTotal(1)
                .shardBatchId(UUID.randomUUID().toString().replace("-", ""))
                .createTime(LocalDateTime.now())
                .build();
        if (status == TaskRunStatus.skipped) {
            run.setEndTime(LocalDateTime.now());
        }
        taskRunMapper.insert(run);
    }
}