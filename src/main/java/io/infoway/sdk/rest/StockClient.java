package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * Stock market data client (HK, US, CN).
 */
public class StockClient extends MarketDataClient {

    public StockClient(HttpClient http) {
        super(http, "stock");
    }
}
