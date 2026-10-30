package com.cronova.scheduler.service;

import com.cronova.scheduler.domain.po.AKSK;
import com.cronova.scheduler.service.impl.AKSKServiceImpl;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class AKSKServiceImplSignatureTest {

    @Test
    void validatesBodySignatureAndRejectsTamperedBody() throws Exception {
        AKSKServiceImpl service = spy(new AKSKServiceImpl());
        AKSK aksk = AKSK.builder()
                .accessKey("AK-1")
                .secretKey("SK-1")
                .workerId("worker-1")
                .status("active")
                .build();
        doReturn(aksk).when(service).getByAccessKey("AK-1");

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String body = "{\"name\":\"调度器\"}";
        String signature = hmac("SK-1", timestamp + "\n" + body);

        assertTrue(service.validateAKSK("AK-1", signature, timestamp, body));
        assertFalse(service.validateAKSK("AK-1", signature, timestamp, body + " "));
    }

    private String hmac(String secret, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
