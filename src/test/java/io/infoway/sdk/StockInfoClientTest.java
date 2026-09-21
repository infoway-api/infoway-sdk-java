package io.infoway.sdk;

import io.infoway.sdk.rest.StockInfoClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockInfoClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private StockInfoClient stockInfo;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        httpClient = HttpClient.builder()
                .apiKey("test-key")
                .baseUrl(server.url("/").toString())
                .timeout(5)
                .maxRetries(1)
                .build();
        stockInfo = new StockInfoClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        server.shutdown();
    }

    private void enqueueOk() {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":{}}")
                .addHeader("Content-Type", "application/json"));
    }

    @Test
    void pathsAndLangQueryMatchV2Contract() throws Exception {
        enqueueOk();
        stockInfo.getCompany("AAPL.US", Lang.ZH_CN);
        assertEquals("/common/v2/basic/stock/company/AAPL.US?lang=zh-CN",
                server.takeRequest().getPath());

        enqueueOk();
        stockInfo.getValuation("AAPL.US");
        assertEquals("/common/v2/basic/stock/valuation/AAPL.US", server.takeRequest().getPath());

        enqueueOk();
        stockInfo.getRatings("AAPL.US", "en");
        assertEquals("/common/v2/basic/stock/ratings/AAPL.US?lang=en", server.takeRequest().getPath());

        enqueueOk();
        stockInfo.getPanorama("AAPL.US", Lang.EN);
        assertEquals("/common/v2/basic/stock/panorama/AAPL.US?lang=en", server.takeRequest().getPath());

        enqueueOk();
        stockInfo.getConcepts("AAPL.US");
        assertEquals("/common/v2/basic/stock/concepts/AAPL.US", server.takeRequest().getPath());

        enqueueOk();
        stockInfo.getEvents("AAPL.US", 15, Lang.ZH_CN);
        String events = server.takeRequest().getPath();
        assertTrue(events.startsWith("/common/v2/basic/stock/events/AAPL.US?"), events);
        assertTrue(events.contains("limit=15"), events);
        assertTrue(events.contains("lang=zh-CN"), events);

        enqueueOk();
        stockInfo.getDrivers("AAPL.US", Lang.EN);
        assertEquals("/common/v2/basic/stock/drivers/AAPL.US?lang=en", server.takeRequest().getPath());
    }
}
