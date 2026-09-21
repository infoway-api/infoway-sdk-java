package io.infoway.sdk;

import io.infoway.sdk.rest.MarketClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MarketClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private MarketClient market;

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
        market = new MarketClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        server.shutdown();
    }

    private void enqueueOk() {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":{\"ok\":true}}")
                .addHeader("Content-Type", "application/json"));
    }

    @Test
    void getTemperatureDefaultIsHkUsCn() throws Exception {
        enqueueOk();
        market.getTemperature();

        String path = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(path.startsWith("/common/v2/basic/market/temperature?"), path);
        assertTrue(path.contains("market=HK,US,CN"), path);
        assertFalse(path.contains("SG"), path);
    }

    @Test
    void getTemperatureSendsLang() throws Exception {
        enqueueOk();
        market.getTemperature("HK,US", "zh-CN");

        String path = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(path.contains("market=HK,US"), path);
        assertTrue(path.contains("lang=zh-CN"), path);
    }

    @Test
    void getTurnoverAndOverviewHitNewPaths() throws Exception {
        enqueueOk();
        market.getTurnover("CN", "zh-CN");
        String turnover = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(turnover.startsWith("/common/v2/basic/market/turnover/CN"), turnover);
        assertTrue(turnover.contains("lang=zh-CN"), turnover);

        enqueueOk();
        market.getOverview("HK");
        assertEquals("/common/v2/basic/market/overview/HK", server.takeRequest().getPath());
    }

    @Test
    void getRankCategoriesAndRankMatchDocs() throws Exception {
        enqueueOk();
        market.getRankCategories("CN", "zh-CN");
        String cats = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(cats.startsWith("/common/v2/basic/market/rank/categories/CN"), cats);
        assertTrue(cats.contains("lang=zh-CN"), cats);

        enqueueOk();
        market.getRank("CN", "all", "chg", "desc", 30, 0, "en");
        String rank = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(rank.startsWith("/common/v2/basic/market/rank/CN/all?"), rank);
        assertTrue(rank.contains("sort=chg"), rank);
        assertTrue(rank.contains("order=desc"), rank);
        assertTrue(rank.contains("limit=30"), rank);
        assertTrue(rank.contains("offset=0"), rank);
        assertTrue(rank.contains("lang=en"), rank);
    }

    @Test
    void enumOverloadsSendTheSameWireValues() throws Exception {
        enqueueOk();
        market.getTemperature(Lang.ZH_CN, Market.HK, Market.US);
        String temp = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(temp.contains("market=HK,US"), temp);
        assertTrue(temp.contains("lang=zh-CN"), temp);

        enqueueOk();
        market.getRank(Market.CN, "all", RankSort.CHG, SortOrder.DESC, 30, 0, Lang.EN);
        String rank = java.net.URLDecoder.decode(server.takeRequest().getPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(rank.startsWith("/common/v2/basic/market/rank/CN/all?"), rank);
        assertTrue(rank.contains("sort=chg"), rank);
        assertTrue(rank.contains("order=desc"), rank);
        assertTrue(rank.contains("lang=en"), rank);
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedRankConfigKeepsOldPath() throws Exception {
        enqueueOk();
        market.getRankConfig("US");
        assertEquals("/common/v2/basic/market/rank-config/US", server.takeRequest().getPath());
    }
}
