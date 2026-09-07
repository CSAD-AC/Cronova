package com.sjdt.scheduler.util;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cron 表达式"下一个触发时间"计算工具
 * <p>
 * 解析结果缓存在内存（ConcurrentHashMap）：Cron 表达式数量有限，
 * 且只在任务配置变更时新增条目，无需引入外部缓存。
 */
@Slf4j
public final class CronNextTimeUtil {

    /** 与调度器一致：统一使用 Asia/Shanghai 时区计算 */
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    private static final CronParser PARSER;
    private static final ConcurrentHashMap<String, ExecutionTime> CACHE = new ConcurrentHashMap<>();

    static {
        CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ);
        PARSER = new CronParser(cronDefinition);
    }

    private CronNextTimeUtil() {
    }

    /**
     * 计算 base 之后的下一个理论触发时间（严格晚于 base）
     *
     * @param base     基准时间；触发主流程传 firedAt（当前 next_trigger_time），misfire 对齐传 now
     * @param cronExpr Quartz 风格 Cron 表达式
     * @return 下一个触发时间
     * @throws IllegalArgumentException cron 表达式为空或没有未来触发点
     */
    public static LocalDateTime next(LocalDateTime base, String cronExpr) {
        if (cronExpr == null || cronExpr.isEmpty()) {
            throw new IllegalArgumentException("Cron表达式为空");
        }
        ExecutionTime executionTime = CACHE.computeIfAbsent(cronExpr, expr -> ExecutionTime.forCron(PARSER.parse(expr)));
        ZonedDateTime zonedBase = base.atZone(ZONE_ID);
        Optional<ZonedDateTime> nextOpt = executionTime.nextExecution(zonedBase);
        if (nextOpt.isEmpty()) {
            throw new IllegalArgumentException("Cron表达式没有未来触发点: " + cronExpr);
        }
        return nextOpt.get().withZoneSameInstant(ZONE_ID).toLocalDateTime();
    }

    /**
     * 清理解析缓存（可选，日常无需调用）
     */
    public static void clearCache() {
        CACHE.clear();
    }
}
