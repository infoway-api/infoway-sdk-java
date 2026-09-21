package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * Taiwan market data client (TWSE / TPEX). Use {@code .TW} codes, e.g. {@code 2330.TW}.
 */
public class TaiwanClient extends MarketDataClient {

    public TaiwanClient(HttpClient http) {
        super(http, "taiwan");
    }
}
