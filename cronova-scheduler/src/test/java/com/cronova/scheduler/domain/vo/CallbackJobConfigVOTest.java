package com.cronova.scheduler.domain.vo;

import com.cronova.scheduler.domain.po.CallbackJobConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackJobConfigVOTest {

    @Test
    void doesNotSerializeSecretKey() throws Exception {
        CallbackJobConfigVO vo = CallbackJobConfigVO.from(CallbackJobConfig.builder()
                .id("config-1")
                .taskId("task-1")
                .secretKey("top-secret")
                .build());

        String json = new ObjectMapper().writeValueAsString(vo);

        assertFalse(json.contains("top-secret"));
        assertFalse(json.contains("\"secretKey\""));
        assertTrue(json.contains("\"hasSecretKey\":true"));
    }
}
