package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.exception.InfowayTimeoutException;
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
import java.util.concurrent.TimeUnit;

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

    HttpClient(String apiKey, String baseUrl, long timeoutSeconds, int maxRetries) {
        this.apiKey = apiKey != null ? apiKey : System.getenv().getOrDefault("INFOWAY_API_KEY", "");
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : DEFAULT_BASE_URL;
        this.maxRetries = maxRetries > 0 ? maxRetries : DEFAULT_MAX_RETRIES;
        this.gson = new Gson();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
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
            try (Response response = client.newCall(request).execute()) {
                return handleResponse(response);
            } catch (InfowayApiException e) {
                throw e;
            } catch (SocketTimeoutException e) {
                lastException = new InfowayTimeoutException(e.getMessage(), e);
            } catch (IOException e) {
                lastException = e;
                log.debug("Request failed (attempt {}/{}): {}", attempt + 1, maxRetries, e.getMessage());
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
        if (lastException instanceof InfowayTimeoutException) {
            throw (InfowayTimeoutException) lastException;
        }
        throw new RuntimeException("Request failed after " + maxRetries + " retries", lastException);
    }

    private JsonElement handleResponse(Response response) throws IOException {
        if (response.code() == 401) {
            throw new InfowayAuthException();
        }
        String responseBody = response.body() != null ? response.body().string() : "{}";
        JsonObject data = gson.fromJson(responseBody, JsonObject.class);

        int ret = 200;
        if (data.has("ret")) {
            ret = data.get("ret").getAsInt();
        } else if (data.has("code")) {
            ret = data.get("code").getAsInt();
        }

        String msg = data.has("msg") ? data.get("msg").getAsString() : "";
        String traceId = data.has("traceId") ? data.get("traceId").getAsString() : null;

        if (ret == 401) {
            throw new InfowayAuthException(msg);
        }
        if (ret != 200) {
            throw new InfowayApiException(ret, msg, traceId);
        }

        return data.has("data") ? data.get("data") : null;
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

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
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
