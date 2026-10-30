package com.cronova.scheduler.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CachedBodyHttpServletRequestTest {

    @Test
    void cachesBodyAndAllowsRepeatedReads() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("{\"name\":\"cronova\"}".getBytes(StandardCharsets.UTF_8));

        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);

        assertEquals("{\"name\":\"cronova\"}", cached.getBody());
        assertEquals("{\"name\":\"cronova\"}", new String(cached.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        assertEquals("{\"name\":\"cronova\"}", new String(cached.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsBodyLargerThanOneMegabyte() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[1024 * 1024 + 1]);

        assertThrows(IOException.class, () -> new CachedBodyHttpServletRequest(request));
    }
}
