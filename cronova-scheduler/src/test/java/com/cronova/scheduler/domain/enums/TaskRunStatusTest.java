package com.cronova.scheduler.domain.enums;

import com.cronova.scheduler.exception.InvalidEnumValueException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 任务运行状态枚举（步骤24）测试
 * 验证阻塞策略新增的 queued/skipped 状态存在、支持字符串反查、未知状态抛业务异常。
 */
class TaskRunStatusTest {

    private static final String ALL_VALUES =
            "pending,running,paused,success,failed,timeout,queued,skipped";

    @Test
    @DisplayName("枚举值-状态全集完整可反查")
    void testAllStatusesResolvable() {
        for (String v : ALL_VALUES.split(",")) {
            assertEquals(v, TaskRunStatus.of(v).getValue(), "状态 " + v + " 应可反查");
        }
    }

    @Test
    @DisplayName("枚举值-包含阻塞策略新增的queued/skipped")
    void testContainsBlockStatuses() {
        assertTrue(Arrays.stream(TaskRunStatus.values())
                .anyMatch(s -> s == TaskRunStatus.queued), "应包含queued（排队中）");
        assertTrue(Arrays.stream(TaskRunStatus.values())
                .anyMatch(s -> s == TaskRunStatus.skipped), "应包含skipped（被阻塞丢弃）");
    }

    @Test
    @DisplayName("枚举值-大小写不敏感反查")
    void testCaseInsensitiveResolve() {
        assertEquals(TaskRunStatus.queued, TaskRunStatus.of("QUEUED"));
        assertEquals(TaskRunStatus.skipped, TaskRunStatus.of("Skipped"));
    }

    @Test
    @DisplayName("枚举值-未知状态抛业务异常")
    void testUnknownThrows() {
        assertThrows(InvalidEnumValueException.class, () -> TaskRunStatus.of("unknown"));
    }
}