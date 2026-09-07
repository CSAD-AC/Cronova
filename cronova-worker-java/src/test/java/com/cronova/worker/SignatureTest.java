package com.cronova.worker;

import com.cronova.worker.client.AkskHttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * AKSK 签名测试（RFC 4231 标准向量，验证与调度器 HMAC-SHA256 一致）
 */
class SignatureTest {

    @Test
    void hmacSha256MatchesRfc4231Vector() {
        // RFC 4231 Test Case 2: key="key", msg="The quick brown fox jumps over the lazy dog"
        String expected = "f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8";
        String actual = AkskHttpClient.hmacSha256("key", "The quick brown fox jumps over the lazy dog");
        assertEquals(expected, actual);
    }
}
