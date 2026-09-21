package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;

/**
 * Current API-key quota ({@code GET /package/info}).
 *
 * <p>Fields: {@code packageName}, {@code expireTime}, {@code apiNumPerSec},
 * {@code maxWsConNum}, {@code maxNum}, {@code maxYearHisData}, {@code allWsNum}.</p>
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
