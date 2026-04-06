package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * Crypto market data client.
 */
public class CryptoClient extends MarketDataClient {

    public CryptoClient(HttpClient http) {
        super(http, "crypto");
    }
}
