package com.cronova.worker;

import com.cronova.worker.client.AkskHttpClient;
import com.cronova.worker.common.WorkerContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AkskHttpClientHttpTest {

    @Test
    void sendsEmptyObjectBodyAndSignsTheExactWireBytes() throws Exception {
        assertSignedBody(Map.of(), "{}");
    }

    @Test
    void sendsUnicodeBodyAndSignsTheExactWireBytes() throws Exception {
        assertSignedBody(Map.of("name", "调度器"), "{\"name\":\"调度器\"}");
    }

    private void assertSignedBody(Map<String, Object> payload, String expectedBody) throws Exception {
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        AtomicReference<String> receivedTimestamp = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/worker/heartbeat", exchange -> handle(exchange, receivedBody,
                receivedSignature, receivedTimestamp));
        server.start();
        try {
            WorkerContext context = new WorkerContext();
            context.register("worker-1", "AK-1", "SK-1", "localhost", "127.0.0.1", null);
            AkskHttpClient client = new AkskHttpClient(context, new ObjectMapper());

            AkskHttpClient.ApiResponse response = client.post(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/worker/heartbeat",
                    payload, null);

            assertTrue(response.isOk());
            assertEquals(expectedBody, receivedBody.get());
            assertEquals(AkskHttpClient.hmacSha256("SK-1",
                    receivedTimestamp.get() + "\n" + expectedBody), receivedSignature.get());
        } finally {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange, AtomicReference<String> receivedBody,
                        AtomicReference<String> receivedSignature, AtomicReference<String> receivedTimestamp)
            throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String signature = exchange.getRequestHeaders().getFirst("X-Signature");
        String timestamp = exchange.getRequestHeaders().getFirst("X-Timestamp");
        receivedBody.set(body);
        receivedSignature.set(signature);
        receivedTimestamp.set(timestamp);

        String expected = AkskHttpClient.hmacSha256("SK-1", timestamp + "\n" + body);
        int status = expected.equals(signature) ? 200 : 401;
        byte[] response = ("{\"code\":" + status + ",\"message\":\"ok\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
