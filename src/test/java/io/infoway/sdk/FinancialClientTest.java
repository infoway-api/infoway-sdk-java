package io.infoway.sdk;

import io.infoway.sdk.rest.FinancialClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FinancialClientTest {

    private MockWebServer server;
    private HttpClient httpClient;
    private FinancialClient financial;

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
        financial = new FinancialClient(httpClient);
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
    void earningStatusSendsSymbolAndType() throws Exception {
        enqueueOk();
        financial.getEarningStatus("AAPL.US", SymbolType.STOCK_US);

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/common/basic/financial/earning_status?"), path);
        assertTrue(path.contains("symbol=AAPL.US"), path);
        assertTrue(path.contains("type=STOCK_US"), path);
        assertFalse(path.contains("period_type="), path);
    }

    @Test
    void incomeStatementSendsPeriodType() throws Exception {
        enqueueOk();
        financial.getIncomeStatement("000001.SZ", "STOCK_CN", "fq");

        String path = server.takeRequest().getPath();
        assertTrue(path.startsWith("/common/basic/financial/income_statement?"), path);
        assertTrue(path.contains("symbol=000001.SZ"), path);
        assertTrue(path.contains("type=STOCK_CN"), path);
        assertTrue(path.contains("period_type=fq"), path);
    }

    @Test
    void periodTypeEnumSendsFq() throws Exception {
        enqueueOk();
        financial.getIncomeStatement("AAPL.US", SymbolType.STOCK_US, PeriodType.FQ);

        String path = server.takeRequest().getPath();
        assertTrue(path.contains("period_type=fq"), path);
        assertTrue(path.contains("type=STOCK_US"), path);
    }

    @Test
    void remainingPathsMatchDocs() throws Exception {
        enqueueOk();
        financial.getRevenue("AAPL.US", "STOCK_US");
        assertTrue(server.takeRequest().getPath().startsWith("/common/basic/financial/revenue?"));

        enqueueOk();
        financial.getCashFlow("AAPL.US", SymbolType.STOCK_US);
        assertTrue(server.takeRequest().getPath().startsWith("/common/basic/financial/cash_flow?"));

        enqueueOk();
        financial.getBalanceSheet("AAPL.US", "STOCK_US", "fy");
        assertTrue(server.takeRequest().getPath().startsWith("/common/basic/financial/balance_sheet?"));

        enqueueOk();
        financial.getStatistics("AAPL.US", "STOCK_US");
        assertTrue(server.takeRequest().getPath().startsWith("/common/basic/financial/statistics?"));

        enqueueOk();
        financial.getDividend("00700.HK", "STOCK_HK");
        assertTrue(server.takeRequest().getPath().startsWith("/common/basic/financial/dividend?"));

        enqueueOk();
        financial.getDividendPayout("AAPL.US", SymbolType.STOCK_US);
        RecordedRequest payout = server.takeRequest();
        assertTrue(payout.getPath().startsWith("/common/basic/financial/dividend_payout?"), payout.getPath());
        assertFalse(payout.getPath().contains("period_type="), payout.getPath());

        enqueueOk();
        financial.getEarnings("AAPL.US", "STOCK_US", "fq");
        String earnings = server.takeRequest().getPath();
        assertTrue(earnings.startsWith("/common/basic/financial/earnings?"), earnings);
        assertTrue(earnings.contains("period_type=fq"), earnings);
    }
}
