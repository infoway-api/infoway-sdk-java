package io.infoway.sdk;

import io.infoway.sdk.rest.PackageClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackageClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private PackageClient packages;

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
        packages = new PackageClient(httpClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        httpClient.close();
        server.shutdown();
    }

    @Test
    void getInfoHitsPackagePath() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("{\"ret\":200,\"msg\":\"success\",\"data\":{\"packageName\":\"Premium\",\"apiNumPerSec\":20}}")
                .addHeader("Content-Type", "application/json"));

        var data = packages.getInfo();
        var request = server.takeRequest();
        assertEquals("/package/info", request.getPath());
        assertEquals("test-key", request.getHeader("apiKey"));
        assertEquals("Premium", data.getAsJsonObject().get("packageName").getAsString());
    }
}
