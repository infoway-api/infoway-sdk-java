package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayRateLimitException;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.exception.InfowayTimeoutException;
import io.infoway.sdk.exception.InfowayIoException;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Low-level HTTP client with retry and error handling.
 *
 * <p>Wraps OkHttp and handles authentication, retries with exponential backoff,
 * and Infoway API response parsing.</p>
 */
public class HttpClient implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(HttpClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String DEFAULT_BASE_URL = "https://data.infoway.io";
    private static final long DEFAULT_TIMEOUT_SECONDS = 15;
    private static final int DEFAULT_MAX_RETRIES = 3;

    private final String apiKey;
    private final String baseUrl;
    private final int maxRetries;
    private final OkHttpClient client;
    private final Gson gson;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();

    HttpClient(String apiKey, String baseUrl, long timeoutSeconds, int maxRetries) {
        this.apiKey = ApiKeys.resolve(apiKey);
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.gson = new Gson();
        this.client = HttpTransports.http(timeoutSeconds);
    }

    /**
     * Send a GET request.
     *
     * @param path   API path (e.g. "/stock/batch_trade/AAPL.US")
     * @param params optional query parameters
     * @return the "data" field from the API response as a JsonElement
     */
    public JsonElement get(String path, Map<String, String> params) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
        if (params != null) {
            params.forEach(urlBuilder::addQueryParameter);
        }
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .addHeader("apiKey", apiKey)
                .get()
                .build();
        return execute(request);
    }

    /**
     * Send a GET request without query parameters.
     *
     * @param path API path
     * @return the "data" field from the API response
     */
    public JsonElement get(String path) {
        return get(path, null);
    }

    /**
     * Send a POST request with a JSON body.
     *
     * @param path API path
     * @param body request body as a JsonObject
     * @return the "data" field from the API response
     */
    public JsonElement post(String path, JsonObject body) {
        String json = body != null ? gson.toJson(body) : "{}";
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .addHeader("apiKey", apiKey)
                .post(RequestBody.create(json, JSON))
                .build();
        return execute(request);
    }

    private JsonElement execute(Request request) {
        Exception lastException = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            ensureOpen();
            Call call = client.newCall(request);
            calls.add(call);
            try (Response response = call.execute()) {
                ensureOpen();
                return handleResponse(response);
            } catch (IllegalStateException e) {
                throw e;
            } catch (InfowayRateLimitException e) {
                // rate limiting is transient — back off and retry, rethrow if retries run out
                lastException = e;
                log.debug("Rate limited (attempt {}/{}): {}", attempt + 1, maxRetries, e.getMsg());
            } catch (InfowayApiException e) {
                throw e;
            } catch (SocketTimeoutException e) {
                lastException = new InfowayTimeoutException(e.getMessage(), e);
            } catch (IOException e) {
                lastException = e;
                log.debug("Request failed (attempt {}/{}): {}", attempt + 1, maxRetries, e.getMessage());
            } finally {
                calls.remove(call);
            }
            if (attempt < maxRetries - 1) {
                try {
                    Thread.sleep(Math.min((long) Math.pow(2, attempt) * 1000, 8000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }
            }
        }
        if (lastException instanceof InfowayRateLimitException) {
            throw (InfowayRateLimitException) lastException;
        }
        if (lastException instanceof InfowayTimeoutException) {
            throw (InfowayTimeoutException) lastException;
        }
        throw new InfowayIoException("Request failed after " + maxRetries + " retries", lastException);
    }

    /**
     * Turn a raw HTTP response into either the payload or a typed exception.
     *
     * <p>The order below is load-bearing (see docs/specs/2026-08-15-sdk-contract-fix-design.md §1).
     * Production serves five different envelopes and only two of them carry {@code ret}/{@code code};
     * the previous implementation defaulted the missing status to 200, judged every error a success
     * and returned {@code null} for it.</p>
     *
     * <ol>
     *   <li>HTTP 401 → {@link InfowayAuthException}</li>
     *   <li>HTTP 429 → {@link InfowayRateLimitException}</li>
     *   <li>body has {@code detail} but no {@code ret}/{@code code}/{@code data}:
     *       "rate limit" in the detail → {@link InfowayRateLimitException} (status may be 200!),
     *       otherwise {@link InfowayApiException}</li>
     *   <li>body has {@code title} + {@code status} (RFC 7807 problem+json) → {@link InfowayApiException}</li>
     *   <li>{@code ret}/{@code code} present and != 200 → classified with {@link RestErrorCode}
     *       (501/502 → {@link InfowayRateLimitException}). Runs <em>before</em> the HTTP-status
     *       branch so commonApi HTTP 400 bodies keep their {@code msg}.</li>
     *   <li>HTTP &gt;= 400 with no business {@code ret} → {@link InfowayApiException} (gateway pages)</li>
     *   <li>success: return {@code body["data"]} when present, otherwise the whole body</li>
     * </ol>
     */
    private JsonElement handleResponse(Response response) throws IOException {
        int status = response.code();
        String responseBody = response.body() != null ? response.body().string() : "";
        JsonElement parsed = parseJson(responseBody);
        JsonObject body = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        String traceId = optString(body, "traceId");

        // 1 — HTTP 401. The body uses "message", not "msg".
        if (status == 401) {
            throw new InfowayAuthException(errorMessage(body, responseBody, "Unauthorized"));
        }

        // 2 — HTTP 429.
        if (status == 429) {
            throw InfowayRateLimitException.rest(429,
                    errorMessage(body, responseBody, "Rate limit exceeded"), traceId);
        }

        // 3 — bare {"detail": ...} envelope (no ret/code/data). Status is often 200.
        if (body != null && body.has("detail")
                && !body.has("ret") && !body.has("code") && !body.has("data")) {
            String detail = optString(body, "detail");
            if (detail != null && detail.toLowerCase().contains("rate limit")) {
                throw new InfowayRateLimitException(status, detail, traceId, "RATE_LIMIT");
            }
            throw InfowayApiException.ofHttpStatus(status, detail, traceId);
        }

        // 4 — RFC 7807 problem+json: {"type","title","status","detail","instance"}.
        if (body != null && body.has("title") && body.has("status")) {
            int problemStatus = optInt(body, "status", status);
            String detail = body.has("detail") ? optString(body, "detail") : optString(body, "title");
            throw InfowayApiException.ofHttpStatus(problemStatus, detail, traceId);
        }

        // 5 — business status. commonApi sets HTTP 400/500 with the same ret in the body.
        Integer ret = businessRet(body);
        if (ret != null && ret != 200) {
            throw restFailure(ret, errorMessage(body, responseBody, "API error"), traceId);
        }

        // 6 — any other HTTP error, including non-JSON gateway pages and empty bodies.
        if (status >= 400) {
            throw InfowayApiException.ofHttpStatus(status, bodySnippet(responseBody, status), traceId);
        }

        // 7 — success.
        if (body == null) {
            if (parsed != null && parsed.isJsonArray()) {
                return parsed;   // no endpoint does this today, but it is valid JSON payload
            }
            throw new InfowayApiException(status, "Non-JSON response: " + bodySnippet(responseBody, status), traceId);
        }
        return body.has("data") ? body.get("data") : body;
    }

    private static Integer businessRet(JsonObject body) {
        if (body == null) {
            return null;
        }
        if (body.has("ret") && body.get("ret").isJsonPrimitive()) {
            return optInt(body, "ret", 200);
        }
        if (body.has("code") && body.get("code").isJsonPrimitive()) {
            return optInt(body, "code", 200);
        }
        return null;
    }

    private static InfowayApiException restFailure(int ret, String msg, String traceId) {
        ret = RestErrorCode.classify(ret, msg);
        if (ret == 401) {
            return new InfowayAuthException(msg);
        }
        if (ret == 429
                || ret == RestErrorCode.REQUEST_EXCEED_LIMIT.getCode()
                || ret == RestErrorCode.REQUEST_FOR_DAY_LIMIT.getCode()) {
            return InfowayRateLimitException.rest(ret, msg, traceId);
        }
        return InfowayApiException.ofRest(ret, msg, traceId);
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("InfowayClient is closed");
        }
    }

    private JsonElement parseJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return gson.fromJson(text, JsonElement.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads msg / message / detail / title — the four names production uses for errors. */
    private static String errorMessage(JsonObject body, String rawBody, String fallback) {
        if (body != null) {
            for (String key : new String[]{"msg", "message", "detail", "title"}) {
                String value = optString(body, key);
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }
        if (rawBody != null && !rawBody.isBlank()) {
            return fallback + ": " + bodySnippet(rawBody, 0);
        }
        return fallback;
    }

    private static String bodySnippet(String rawBody, int status) {
        if (rawBody == null || rawBody.isBlank()) {
            return "HTTP " + status + " with empty body";
        }
        String trimmed = rawBody.trim();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "…" : trimmed;
    }

    private static String optString(JsonObject body, String key) {
        if (body == null || !body.has(key) || body.get(key).isJsonNull()) {
            return null;
        }
        JsonElement value = body.get(key);
        return value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    private static int optInt(JsonObject body, String key, int fallback) {
        try {
            return body.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /**
     * Get the base URL this client is configured to use.
     *
     * @return base URL string
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Get the API key this client is configured with.
     *
     * @return API key string
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Rejects later calls and cancels in-flight ones. Idempotent.
     * The shared OkHttp dispatcher stays up for other clients.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Call call : calls) {
            call.cancel();
        }
        calls.clear();
    }

    /**
     * Create a new HttpClient builder.
     */
    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxRetries = DEFAULT_MAX_RETRIES;

        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        HttpClient build() {
            return new HttpClient(apiKey, baseUrl, timeoutSeconds, maxRetries);
        }
    }
}
