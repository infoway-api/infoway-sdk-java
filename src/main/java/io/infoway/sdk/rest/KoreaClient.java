package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * Korea market data client (KRX / KOSPI / KOSDAQ). Use {@code .KS} codes, e.g. {@code 005930.KS}.
 */
public class KoreaClient extends MarketDataClient {

    public KoreaClient(HttpClient http) {
        super(http, "korea");
    }
}
