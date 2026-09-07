package com.sjdt.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sjdt.scheduler.common.RedisKey;
import com.sjdt.scheduler.domain.enums.TaskRunStatus;
import com.sjdt.scheduler.domain.po.Task;
import com.sjdt.scheduler.domain.po.TaskRun;
import com.sjdt.scheduler.mapper.TaskMapper;
import com.sjdt.scheduler.mapper.TaskRunMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 分片结算服务
 * <p>
 * 每个 run 进入终态时触发结算检查，满足以下全部条件才对整批汇总：
 * <ol>
 *     <li>批次内分片数齐全（distinct shardIndex == shardTotal）；</li>
 *     <li>批次内不存在任何未终态的 run（含重试产生的新 run 处于 pending/running）；</li>
 *     <li>每个分片取"最新一次尝试"（同 shardIndex 下 retryNum 最大的 run）均为终态，
 *         且失败片的重试预算已耗尽（retryNum &gt;= 任务配置的 retryCount）——
 *         失败但仍有重试预算的分片不算最终失败，避免批次被提前判死。</li>
 * </ol>
 * 汇总规则：任一片最终 failed/timeout -> 批次失败；全部 success -> 批次成功。
 * <p>
 * 使用方式：任何把 TaskRun 推进到终态的路径（MQ 结果消费 / HTTP 结果回流 / 状态上报 /
 * 调度侧失败落库 / 超时监控）在更新完成后调用 {@link #onRunTerminal(String)}。
 * 非分片任务（N=1 特例：shardIndex=0, shardTotal=1）走同一条结算路径，不写两套逻辑。
 * <p>
 * 幂等保障：多片几乎同时终态时可能并发触发结算，用 Redis SETNX（scheduler:shard:settle:{batchId}）
 * 保证同一批次只真正结算一次。
 */
@Slf4j
@Service
public class ShardSettlementService {

    /** 结算标记 TTL：远大于分片任务单轮执行周期即可，过期后标记自动清理 */
    private static final Duration SETTLE_MARK_TTL = Duration.ofHours(24);

    /** 任务运行实例Mapper */
    private final TaskRunMapper taskRunMapper;

    /** 任务定义Mapper（读取重试预算配置） */
    private final TaskMapper taskMapper;

    /** Redis操作模板（结算幂等标记） */
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public ShardSettlementService(TaskRunMapper taskRunMapper, TaskMapper taskMapper,
                                  RedisTemplate<String, String> redisTemplate) {
        this.taskRunMapper = taskRunMapper;
        this.taskMapper = taskMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 分片结算钩子：某个 run 进入终态后调用，检查其所属批次是否满足结算条件
     * 内部自行判断是否为终态、是否带分片身份，非终态或历史数据（无批次ID）直接返回
     *
     * @param taskRunId 进入终态的任务运行实例ID
     */
    public void onRunTerminal(String taskRunId) {
        try {
            TaskRun run = taskRunMapper.selectById(taskRunId);
            if (run == null || run.getShardBatchId() == null || run.getShardTotal() == null) {
                // 历史数据未写分片字段（shardBatchId 为 NULL），不参与结算
                return;
            }
            if (!isTerminal(run.getStatus())) {
                return;
            }
            settleIfComplete(run);
        } catch (Exception e) {
            // 结算失败不影响结果回流主流程
            log.error("分片结算检查异常，TaskRunID: {}", taskRunId, e);
        }
    }

    /**
     * 判断状态是否为终态（success / failed / timeout）
     *
     * @param status 运行状态
     * @return true=终态
     */
    private boolean isTerminal(TaskRunStatus status) {
        return status == TaskRunStatus.success
                || status == TaskRunStatus.failed
                || status == TaskRunStatus.timeout;
    }

    /**
     * 检查批次是否满足结算条件，满足则执行一次性汇总
     *
     * @param terminalRun 刚进入终态的分片run（携带批次信息）
     */
    private void settleIfComplete(TaskRun terminalRun) {
        String batchId = terminalRun.getShardBatchId();
        int shardTotal = terminalRun.getShardTotal();

        List<TaskRun> batchRuns = taskRunMapper.selectList(new LambdaQueryWrapper<TaskRun>()
                .eq(TaskRun::getShardBatchId, batchId));

        // 条件1：分片数齐全（防御：批次数据异常时不结算）
        long distinctShards = batchRuns.stream().map(TaskRun::getShardIndex).distinct().count();
        if (distinctShards < shardTotal) {
            log.debug("分片批次片数不齐，批次: {}, 已知片数: {}/{}", batchId, distinctShards, shardTotal);
            return;
        }

        // 条件2：批次内不存在任何未终态的 run（含重试新 run 处于 pending/running）
        boolean hasActive = batchRuns.stream().anyMatch(r -> !isTerminal(r.getStatus()));
        if (hasActive) {
            log.debug("分片批次仍有运行中的尝试，批次: {}", batchId);
            return;
        }

        // 条件3：每片取最新一次尝试（同片按 retryNum 最大，同号取创建时间最新）
        Map<Integer, TaskRun> latestPerShard = latestAttemptPerShard(batchRuns);

        // 失败片若仍有重试预算（retryNum < 任务配置 retryCount），说明重试可能即将/已经调度，
        // 不能据此判定批次终态——待重试结果出来后再次触发结算
        int maxRetryCount = retryBudgetOf(terminalRun.getTaskId());
        for (TaskRun latest : latestPerShard.values()) {
            if (latest.getStatus() != TaskRunStatus.success
                    && retryNumOf(latest) < maxRetryCount) {
                log.debug("分片最新尝试失败但仍有重试预算，暂不结算，批次: {}, 分片: {}, 重试: {}/{}",
                        batchId, latest.getShardIndex(), retryNumOf(latest), maxRetryCount);
                return;
            }
        }

        // SETNX 幂等：多片同时终态时只有一个线程能拿到标记，避免重复结算
        String markKey = RedisKey.SHARD_SETTLE_PREFIX + batchId;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(markKey, "1", SETTLE_MARK_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.debug("分片批次已结算过，跳过，批次: {}", batchId);
            return;
        }

        long failedCount = latestPerShard.values().stream()
                .filter(r -> r.getStatus() == TaskRunStatus.failed || r.getStatus() == TaskRunStatus.timeout)
                .count();
        boolean batchSuccess = failedCount == 0;

        if (batchSuccess) {
            log.info("分片批次结算：成功，批次: {}, 任务ID: {}, 总片数: {}",
                    batchId, terminalRun.getTaskId(), shardTotal);
        } else {
            log.warn("分片批次结算：失败（{}片最终失败/超时），批次: {}, 任务ID: {}, 总片数: {}",
                    failedCount, batchId, terminalRun.getTaskId(), shardTotal);
        }
        // TODO 阶段五：此处接入 Dashboard 事件上报（批次视图、分片成功率）
    }

    /**
     * 按分片序号分组，取每片"最新一次尝试"
     * 最新 = retryNum 最大者；retryNum 相同（如超时监控同实例重发）取 createTime 最新者
     *
     * @param batchRuns 批次内全部run
     * @return shardIndex -> 最新run
     */
    private Map<Integer, TaskRun> latestAttemptPerShard(List<TaskRun> batchRuns) {
        Map<Integer, TaskRun> latest = new HashMap<>();
        for (TaskRun run : batchRuns) {
            latest.merge(run.getShardIndex(), run, (a, b) -> isLaterAttempt(b, a) ? b : a);
        }
        return latest;
    }

    /**
     * 判断候选尝试 b 是否比基准尝试 a 更新
     *
     * @param b 候选尝试
     * @param a 基准尝试
     * @return true=b 是更新的尝试
     */
    private boolean isLaterAttempt(TaskRun b, TaskRun a) {
        int ra = retryNumOf(a);
        int rb = retryNumOf(b);
        if (rb != ra) {
            return rb > ra;
        }
        LocalDateTime ta = a.getCreateTime();
        LocalDateTime tb = b.getCreateTime();
        return ta == null || (tb != null && tb.isAfter(ta));
    }

    /**
     * 读取run的重试次数（NULL 视为 0）
     *
     * @param run 任务运行实例
     * @return 重试次数
     */
    private int retryNumOf(TaskRun run) {
        return run.getRetryNum() == null ? 0 : run.getRetryNum();
    }

    /**
     * 读取任务定义配置的最大重试次数（任务不存在或未配置视为 0，即无重试预算）
     *
     * @param taskId 任务ID
     * @return 最大重试次数
     */
    private int retryBudgetOf(String taskId) {
        Task task = taskMapper.selectById(taskId);
        return (task != null && task.getRetryCount() != null) ? task.getRetryCount() : 0;
    }
}
