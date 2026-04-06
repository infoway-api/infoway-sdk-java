package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * India market data client.
 */
public class IndiaClient extends MarketDataClient {

    public IndiaClient(HttpClient http) {
        super(http, "india");
    }
}
