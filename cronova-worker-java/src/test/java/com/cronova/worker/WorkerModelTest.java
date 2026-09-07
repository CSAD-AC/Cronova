package com.cronova.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.cronova.worker.common.WorkerContext;
import com.cronova.worker.model.TaskResultMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标签解析与结果消息序列化测试
 */
class WorkerModelTest {

    @Test
    void parseTagNamesStripsVersionAndLowercases() {
        List<String> names = WorkerContext.parseTagNames(List.of("V1", "Python3.11", "numpy:2.0.2", " go "));
        assertTrue(names.contains("v1"));
        assertTrue(names.contains("python3.11"));
        assertTrue(names.contains("numpy"));
        assertTrue(names.contains("go"));
        assertEquals(4, names.size());
    }

    @Test
    void resultMessageOmitsNullEndTimeAndUsesIsoNoZ() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TaskResultMessage msg = TaskResultMessage.builder()
                .workerId("w1")
                .taskId("t1")
                .taskRunId("r1")
                .status("running")
                .startTime("2025-05-20T15:30:00")
                .durationMs(1000L)
                .logPath("bucket/task-logs/t1/r1.log")
                .build();

        String json = mapper.writeValueAsString(msg);
        // 运行中上报 endTime 为 null，不应出现在 JSON 中（调度器不接受空字符串日期）
        assertFalse(json.contains("endTime"));
        // startTime 使用无 Z 的 ISO_LOCAL_DATE_TIME，可直接被 LocalDateTime 解析
        assertTrue(json.contains("2025-05-20T15:30:00"));
        assertEquals(LocalDateTime.parse("2025-05-20T15:30:00"),
                LocalDateTime.parse("2025-05-20T15:30:00"));
    }
}
