package io.infoway.sdk;

import io.infoway.sdk.rest.PlateClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlateClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private PlateClient plate;

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
        plate = new PlateClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        server.shutdown();
    }

    private void enqueueOk() {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":[]}")
                .addHeader("Content-Type", "application/json"));
    }

    @Test
    void industryConceptMembersIntroChartHitV2Paths() throws Exception {
        enqueueOk();
        plate.getIndustry(Market.HK, 10);
        assertTrue(server.takeRequest().getPath().startsWith("/common/v2/basic/plate/industry/HK?"));

        enqueueOk();
        plate.getConcept("US");
        assertTrue(server.takeRequest().getPath().startsWith("/common/v2/basic/plate/concept/US?"));

        enqueueOk();
        plate.getMembers("IN20293.HK", 0, 20);
        String members = server.takeRequest().getPath();
        assertTrue(members.startsWith("/common/v2/basic/plate/members/IN20293.HK?"), members);
        assertTrue(members.contains("offset=0"), members);
        assertTrue(members.contains("limit=20"), members);

        enqueueOk();
        plate.getIntro("IN20293.HK");
        assertEquals("/common/v2/basic/plate/intro/IN20293.HK", server.takeRequest().getPath());

        enqueueOk();
        plate.getChart(Market.CN);
        assertTrue(server.takeRequest().getPath().startsWith("/common/v2/basic/plate/chart/CN?"));
    }
}
