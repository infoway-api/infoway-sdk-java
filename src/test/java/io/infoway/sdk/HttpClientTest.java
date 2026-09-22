package io.infoway.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientTest {

    private MockWebServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = HttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .timeout(5)
                .maxRetries(1)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    @Test
    void getReturnsDataField() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":{\"price\":150.5}}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = client.get("/test/path");

        assertNotNull(result);
        assertTrue(result.isJsonObject());
        assertEquals(150.5, result.getAsJsonObject().get("price").getAsDouble());

        RecordedRequest request = server.takeRequest();
        assertEquals("GET", request.getMethod());
        assertEquals("/test/path", request.getPath());
        assertEquals("test-key", request.getHeader("apiKey"));
    }

    @Test
    void getWithQueryParams() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[1,2,3]}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = client.get("/test", Map.of("market", "US", "limit", "10"));

        assertNotNull(result);
        assertTrue(result.isJsonArray());
        assertEquals(3, result.getAsJsonArray().size());

        RecordedRequest request = server.takeRequest();
        String path = request.getPath();
        assertTrue(path.contains("market=US"));
        assertTrue(path.contains("limit=10"));
    }

    @Test
    void postSendsJsonBody() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":{\"count\":5}}")
                .addHeader("Content-Type", "application/json"));

        JsonObject body = new JsonObject();
        body.addProperty("codes", "AAPL.US");
        body.addProperty("klineType", 8);
        body.addProperty("klineCount", 100);
        JsonElement result = client.post("/test/kline", body);

        assertNotNull(result);
        assertEquals(5, result.getAsJsonObject().get("count").getAsInt());

        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        String requestBody = request.getBody().readUtf8();
        assertTrue(requestBody.contains("\"codes\":\"AAPL.US\""));
        assertTrue(requestBody.contains("\"klineType\":8"));
    }

    @Test
    void throwsAuthExceptionOnRet401() {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":401,\"msg\":\"Invalid API key\"}")
                .addHeader("Content-Type", "application/json"));

        InfowayAuthException ex = assertThrows(InfowayAuthException.class,
                () -> client.get("/test"));
        assertEquals(401, ex.getRet());
        assertTrue(ex.getMessage().contains("Invalid API key"));
    }

    @Test
    void throwsAuthExceptionOnHttp401() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{}")
                .addHeader("Content-Type", "application/json"));

        assertThrows(InfowayAuthException.class, () -> client.get("/test"));
    }

    @Test
    void throwsApiExceptionOnNon200Ret() {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":500,\"msg\":\"Internal error\",\"traceId\":\"abc123\"}")
                .addHeader("Content-Type", "application/json"));

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/test"));
        assertEquals(500, ex.getRet());
        assertEquals("Internal error", ex.getMsg());
        assertEquals("abc123", ex.getTraceId());
    }

    @Test
    void returnsWholeBodyWhenNoDataField() throws Exception {
        // changed in 0.2.0: several v2 endpoints answer without a "data" key
        // (e.g. plate/intro) and used to be swallowed into null.
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\"}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = client.get("/test");

        assertNotNull(result);
        assertEquals("success", result.getAsJsonObject().get("msg").getAsString());
    }

    @Test
    void handlesCodeFieldInsteadOfRet() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"code\":200,\"msg\":\"ok\",\"data\":{\"value\":42}}")
                .addHeader("Content-Type", "application/json"));

        JsonElement result = client.get("/test");
        assertNotNull(result);
        assertEquals(42, result.getAsJsonObject().get("value").getAsInt());
    }

    @Test
    void closeRejectsLaterCallsAndIsIdempotent() {
        client.close();
        client.close();
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> client.get("/test/path"));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void manyClientsDoNotLeaveNonDaemonOkHttpThreads() throws Exception {
        HttpClient[] extra = new HttpClient[6];
        try {
            for (int i = 0; i < extra.length; i++) {
                server.enqueue(new MockResponse().setBody("{\"ret\":200,\"data\":1}"));
                extra[i] = HttpClient.builder()
                        .apiKey("test-key")
                        .baseUrl(server.url("/").toString())
                        .maxRetries(1)
                        .build();
                extra[i].get("/n");
            }
        } finally {
            for (HttpClient created : extra) {
                if (created != null) {
                    created.close();
                }
            }
        }
        long nonDaemonOkHttp = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> !thread.isDaemon() && thread.getName().startsWith("OkHttp"))
                .count();
        assertEquals(0, nonDaemonOkHttp);
    }
}
