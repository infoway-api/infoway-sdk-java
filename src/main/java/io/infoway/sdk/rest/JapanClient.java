package io.infoway.sdk.rest;

import io.infoway.sdk.HttpClient;

/**
 * Japan market data client.
 */
public class JapanClient extends MarketDataClient {

    public JapanClient(HttpClient http) {
        super(http, "japan");
    }
}
