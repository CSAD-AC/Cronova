package com.cronova.scheduler.interceptor;

import com.cronova.scheduler.common.CachedBodyHttpServletRequest;
import com.cronova.scheduler.domain.po.AKSK;
import com.cronova.scheduler.service.IAKSKService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AKSKInterceptorTest {

    @Mock private IAKSKService akskService;

    private AKSKInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AKSKInterceptor();
        ReflectionTestUtils.setField(interceptor, "akskService", akskService);
        ReflectionTestUtils.setField(interceptor, "objectMapper", new ObjectMapper());
    }

    @Test
    void putsAuthenticatedWorkerIdIntoRequestAttribute() throws Exception {
        MockHttpServletRequest request = signedRequest();
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(akskService.validateAKSK("AK-1", "sig", "1700000000", "{}")).thenReturn(true);
        when(akskService.getByAccessKey("AK-1")).thenReturn(AKSK.builder().workerId("worker-1").build());

        assertTrue(interceptor.preHandle(cached, response, new Object()));
        assertEquals("worker-1", cached.getAttribute(AKSKInterceptor.AUTHENTICATED_WORKER_ID));
    }

    @Test
    void rejectsAkskWithoutBoundWorker() throws Exception {
        MockHttpServletRequest request = signedRequest();
        CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(akskService.validateAKSK("AK-1", "sig", "1700000000", "{}")).thenReturn(true);
        when(akskService.getByAccessKey("AK-1")).thenReturn(AKSK.builder().build());

        assertFalse(interceptor.preHandle(cached, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest signedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Access-Key", "AK-1");
        request.addHeader("X-Signature", "sig");
        request.addHeader("X-Timestamp", "1700000000");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
