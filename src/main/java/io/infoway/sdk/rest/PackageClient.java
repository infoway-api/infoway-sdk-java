package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;

/**
 * Current API-key quota ({@code GET /package/info}).
 *
 * <p>Fields: {@code packageName}, {@code expireTime}, {@code apiNumPerSec},
 * {@code maxWsConNum}, {@code maxNum}, {@code maxYearHisData}, {@code allWsNum}.</p>
 *
 * <p>{@code maxNum} is the package's subscription quota. HTTP batch trade / depth / kline
 * uses a separate server cap, {@code query.max.num} (currently 100) and
 * {@code query.max.klineMaxNum} (currently 500). A request over that cap is
 * {@link io.infoway.sdk.RestErrorCode#PRODUCTS_EXCEEDS_LIMIT} or
 * {@link io.infoway.sdk.RestErrorCode#KLINE_EXCEEDS_LIMIT}, not {@code maxNum}.</p>
 */
public class PackageClient {

    private final HttpClient http;

    public PackageClient(HttpClient http) {
        this.http = http;
    }

    /**
     * Quota for the key sent as header {@code apiKey}.
     *
     * @return package object
     */
    public JsonElement getInfo() {
        return http.get("/package/info");
    }
}
