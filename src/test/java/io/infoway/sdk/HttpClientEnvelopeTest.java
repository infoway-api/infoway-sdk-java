package io.infoway.sdk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.exception.InfowayRateLimitException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The five response envelopes observed in production (design doc §1), replayed
 * verbatim. Before 0.2.0 only envelope #1 and #2 worked: #3/#4/#5 were all judged
 * "ret defaults to 200 → success" and silently returned null — the customer-visible bug.
 */
class HttpClientEnvelopeTest {

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

    private void enqueue(int status, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setBody(body)
                .addHeader("Content-Type", "application/json"));
    }

    // ---- envelope 1: standard {"ret","msg","traceId","data"} -------------------

    @Test
    void standardEnvelopeReturnsDataWithRealFieldNames() {
        enqueue(200, Fixtures.load("rest/trade_stock.json"));

        JsonElement result = client.get("/stock/batch_trade/AAPL.US");

        assertTrue(result.isJsonArray());
        JsonObject tick = result.getAsJsonArray().get(0).getAsJsonObject();
        // the real server sends s/p/v/vw/t/td — never symbol/price
        assertEquals("AAPL.US", tick.get("s").getAsString());
        assertEquals("305.771", tick.get("p").getAsString());
        assertEquals(1786751999691L, tick.get("t").getAsLong());
        assertFalse(tick.has("symbol"));
        assertFalse(tick.has("price"));
    }

    // ---- envelope 2: v2 outer {"market","count","data"} ------------------------

    @Test
    void v2EnvelopeReturnsDataField() {
        enqueue(200, Fixtures.load("rest/plate_industry_v2_envelope.json"));

        JsonElement result = client.get("/common/v2/basic/plate/industry/HK");

        assertTrue(result.isJsonArray());
        assertEquals("IN20234.HK",
                result.getAsJsonArray().get(0).getAsJsonObject().get("symbol").getAsString());
    }

    // ---- envelope 3: no "data" key at all --------------------------------------

    @Test
    void envelopeWithoutDataKeyReturnsWholeBody() {
        enqueue(200, Fixtures.load("rest/plate_intro_no_data_key.json"));

        JsonElement result = client.get("/common/v2/basic/plate/intro/IN20293.HK");

        assertNotNull(result, "no-data-key envelope must return the whole body, not null");
        JsonObject obj = result.getAsJsonObject();
        assertEquals("IN20293.HK", obj.get("plate").getAsString());
        assertTrue(obj.get("intro").getAsString().length() > 10);
    }

    // ---- envelope 4: RFC7807 problem+json on HTTP 400 --------------------------

    @Test
    void rfc7807ProblemJsonThrowsApiException() {
        enqueue(400, Fixtures.load("rest/err_400_rfc7807.json"));

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/common/basic/symbols"));

        assertEquals(400, ex.getRet());
        assertTrue(ex.getMsg().contains("Required parameter 'type' is not present."),
                "detail must be surfaced, got: " + ex.getMsg());
    }

    // ---- envelope 5: rate limit — body says so, HTTP status may be 200 ---------

    @Test
    void rateLimitBodyOnHttp200ThrowsRateLimitException() {
        enqueue(200, Fixtures.load("rest/err_rate_limit.json"));

        InfowayRateLimitException ex = assertThrows(InfowayRateLimitException.class,
                () -> client.get("/stock/batch_trade/AAPL.US"));
        assertTrue(ex.getMsg().toLowerCase().contains("rate limit"));
    }

    @Test
    void http429ThrowsRateLimitException() {
        enqueue(429, Fixtures.load("rest/err_rate_limit.json"));

        assertThrows(InfowayRateLimitException.class, () -> client.get("/stock/batch_trade/AAPL.US"));
    }

    @Test
    void rateLimitExceptionIsAnApiException() {
        enqueue(429, Fixtures.load("rest/err_rate_limit.json"));

        // callers that only catch InfowayApiException must keep working
        assertThrows(InfowayApiException.class, () -> client.get("/stock/batch_trade/AAPL.US"));
    }

    // ---- non-JSON / empty bodies: must not leak Gson or NPE --------------------

    @Test
    void gatewayHtmlPageThrowsApiExceptionNotJsonSyntaxException() {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("<html><head><title>429 Too Many Requests</title></head><body>"
                        + "<center><h1>429 Too Many Requests</h1></center></body></html>")
                .addHeader("Content-Type", "text/html"));

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/stock/batch_trade/AAPL.US"));
        assertEquals(429, ex.getRet());
    }

    @Test
    void emptyBodyOn502ThrowsApiExceptionNotNullPointer() {
        server.enqueue(new MockResponse().setResponseCode(502));

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/stock/batch_trade/AAPL.US"));
        assertEquals(502, ex.getRet());
    }

    @Test
    void http404ThrowsApiExceptionWithDetail() {
        enqueue(404, "{\"detail\":\"Not Found\"}");

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/common/v2/basic/market/rank-config/US"));
        assertEquals(404, ex.getRet());
        assertEquals("Not Found", ex.getMsg());
    }

    // ---- 401: the body field is "message", not "msg" ---------------------------

    @Test
    void http401SurfacesMessageField() {
        enqueue(401, Fixtures.load("rest/err_401.json"));

        InfowayAuthException ex = assertThrows(InfowayAuthException.class,
                () -> client.get("/stock/batch_trade/AAPL.US"));
        assertEquals(401, ex.getRet());
        assertTrue(ex.getMessage().contains("Token invalid"),
                "401 body uses 'message', not 'msg'; got: " + ex.getMessage());
    }

    @Test
    void topLevelJsonArrayIsReturnedAsIs() {
        // Defensive, not a captured contract: no production endpoint answers with a bare
        // array today, but if one starts to, it must not be reported as "non-JSON".
        enqueue(200, "[{\"s\":\"AAPL.US\"}]");

        JsonElement result = client.get("/whatever");

        assertTrue(result.isJsonArray());
        assertEquals("AAPL.US",
                result.getAsJsonArray().get(0).getAsJsonObject().get("s").getAsString());
    }

    // ---- regression guards: behaviour that was already right -------------------

    @Test
    void ret500StillThrowsApiException() {
        enqueue(200, "{\"ret\":500,\"msg\":\"server error\",\"traceId\":\"t1\",\"data\":null}");

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/stock/batch_trade/NOPE"));
        assertEquals(500, ex.getRet());
        assertEquals("server error", ex.getMsg());
        assertEquals("t1", ex.getTraceId());
        assertEquals("SERVER_ERROR", ex.getErrorName());
        assertTrue(ex.getMessage().contains("[500 SERVER_ERROR]"));
    }

    @Test
    void ret500WithBusinessMessageUsesTheServiceCode() {
        enqueue(200, "{\"ret\":500,\"msg\":\"Kline quantity exceeds the limit：500\",\"traceId\":\"k\"}");
        enqueue(200, "{\"ret\":500,\"msg\":\"Products quantity exceeds the limit：100\",\"traceId\":\"p\"}");
        enqueue(200, "{\"ret\":500,\"msg\":\"Timestamp limit error,earliest timestamp：1695361437\",\"traceId\":\"t\"}");

        InfowayApiException kline = assertThrows(InfowayApiException.class,
                () -> client.get("/crypto/v2/batch_kline"));
        assertEquals(503, kline.getRet());
        assertEquals("KLINE_EXCEEDS_LIMIT", kline.getErrorName());

        InfowayApiException products = assertThrows(InfowayApiException.class,
                () -> client.get("/stock/batch_trade/TOO_MANY"));
        assertEquals(505, products.getRet());
        assertEquals("PRODUCTS_EXCEEDS_LIMIT", products.getErrorName());

        InfowayApiException time = assertThrows(InfowayApiException.class,
                () -> client.get("/crypto/v2/batch_kline"));
        assertEquals(513, time.getRet());
        assertEquals("TIME_LIMIT_ERROR", time.getErrorName());
    }

    @Test
    void restProductNotExistsKeeps508NotWsApikeyExpired() {
        enqueue(200, "{\"ret\":508,\"msg\":\"All product not exists\",\"traceId\":\"t1\",\"data\":null}");

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/stock/batch_trade/NOPE"));
        assertEquals(508, ex.getRet());
        assertEquals("PRODUCT_NOT_EXISTS", ex.getErrorName());
        assertEquals("All product not exists", ex.getMsg());
        assertTrue(ex.getMessage().contains("[508 PRODUCT_NOT_EXISTS]"));
    }

    @Test
    void rest501IsRateLimitWithRestErrorName() {
        enqueue(200, "{\"ret\":501,\"msg\":\"Request frequency exceed the limit：1200 times per minute\",\"traceId\":\"t2\"}");

        InfowayRateLimitException ex = assertThrows(InfowayRateLimitException.class,
                () -> client.get("/stock/batch_trade/AAPL.US"));
        assertEquals(501, ex.getRet());
        assertEquals("REQUEST_EXCEED_LIMIT", ex.getErrorName());
    }

    @Test
    void commonApiHttp400SurfacesBodyMsgNotRawJson() {
        enqueue(400, "{\"ret\":400,\"msg\":\"not support type\",\"traceId\":\"t3\",\"data\":null}");

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/common/basic/symbols"));
        assertEquals(400, ex.getRet());
        assertEquals("BAD_REQUEST", ex.getErrorName());
        assertEquals("not support type", ex.getMsg());
    }

    @Test
    void restNoPermissionIs514() {
        enqueue(200, "{\"ret\":514,\"msg\":\"No permission：Korea stock market data\",\"traceId\":\"t4\"}");

        InfowayApiException ex = assertThrows(InfowayApiException.class,
                () -> client.get("/korea/batch_trade/005930.KS"));
        assertEquals(514, ex.getRet());
        assertEquals("NO_PERMISSION", ex.getErrorName());
    }

    @Test
    void rateLimitIsRetriedWithBackoff() throws Exception {
        HttpClient retrying = HttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .timeout(5)
                .maxRetries(2)
                .build();
        try {
            enqueue(429, Fixtures.load("rest/err_rate_limit.json"));
            enqueue(200, Fixtures.load("rest/trade_stock.json"));

            JsonElement result = retrying.get("/stock/batch_trade/AAPL.US");

            assertNotNull(result);
            assertEquals(2, server.getRequestCount(), "429 must be retried, not thrown immediately");
        } finally {
            retrying.close();
        }
    }
}
