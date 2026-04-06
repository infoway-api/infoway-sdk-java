package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * Common market data client.
 */
public class CommonClient extends MarketDataClient {

    public CommonClient(HttpClient http) {
        super(http, "common");
    }
}
