package io.infoway.sdk;

import io.infoway.sdk.rest.BasicClient;
import io.infoway.sdk.rest.CommonClient;
import io.infoway.sdk.rest.CryptoClient;
import io.infoway.sdk.rest.FinancialClient;
import io.infoway.sdk.rest.IndiaClient;
import io.infoway.sdk.rest.JapanClient;
import io.infoway.sdk.rest.KoreaClient;
import io.infoway.sdk.rest.MarketClient;
import io.infoway.sdk.rest.PackageClient;
import io.infoway.sdk.rest.PlateClient;
import io.infoway.sdk.rest.StockClient;
import io.infoway.sdk.rest.StockInfoClient;
import io.infoway.sdk.rest.TaiwanClient;

import java.io.Closeable;

/**
 * Main entry point for the Infoway SDK.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * InfowayClient client = InfowayClient.builder()
 *     .apiKey("YOUR_API_KEY")
 *     .build();
 *
 * JsonElement trades = client.stock().getTrade("AAPL.US");
 * JsonElement klines = client.crypto().getKline("BTCUSDT", KlineType.DAY, 100);
 * JsonElement korea = client.korea().getTrade("005930.KS");
 * JsonElement taiwan = client.taiwan().getTrade("2330.TW");
 * JsonElement income = client.financial().getIncomeStatement("AAPL.US", SymbolType.STOCK_US);
 * JsonElement quota = client.packages().getInfo();
 *
 * client.close();
 * }</pre>
 */
public class InfowayClient implements Closeable {

    private final HttpClient httpClient;
    private final StockClient stockClient;
    private final CryptoClient cryptoClient;
    private final JapanClient japanClient;
    private final IndiaClient indiaClient;
    private final KoreaClient koreaClient;
    private final TaiwanClient taiwanClient;
    private final CommonClient commonClient;
    private final BasicClient basicClient;
    private final MarketClient marketClient;
    private final PlateClient plateClient;
    private final StockInfoClient stockInfoClient;
    private final FinancialClient financialClient;
    private final PackageClient packageClient;

    private InfowayClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.stockClient = new StockClient(httpClient);
        this.cryptoClient = new CryptoClient(httpClient);
        this.japanClient = new JapanClient(httpClient);
        this.indiaClient = new IndiaClient(httpClient);
        this.koreaClient = new KoreaClient(httpClient);
        this.taiwanClient = new TaiwanClient(httpClient);
        this.commonClient = new CommonClient(httpClient);
        this.basicClient = new BasicClient(httpClient);
        this.marketClient = new MarketClient(httpClient);
        this.plateClient = new PlateClient(httpClient);
        this.stockInfoClient = new StockInfoClient(httpClient);
        this.financialClient = new FinancialClient(httpClient);
        this.packageClient = new PackageClient(httpClient);
    }

    /** Stock market data (HK, US, CN). */
    public StockClient stock() { return stockClient; }

    /** Crypto market data. */
    public CryptoClient crypto() { return cryptoClient; }

    /** Japan market data. */
    public JapanClient japan() { return japanClient; }

    /** India market data. */
    public IndiaClient india() { return indiaClient; }

    /** Korea market data ({@code .KS}). */
    public KoreaClient korea() { return koreaClient; }

    /** Taiwan market data ({@code .TW}). */
    public TaiwanClient taiwan() { return taiwanClient; }

    /** Common market data (forex / metals / futures). */
    public CommonClient common() { return commonClient; }

    /** Basic information (symbols, trading days, hours, stock detail). */
    public BasicClient basic() { return basicClient; }

    /** Market overview (temperature, breadth, turnover, ranks). */
    public MarketClient market() { return marketClient; }

    /** Plate / sector data (industry, concept, members). */
    public PlateClient plate() { return plateClient; }

    /** Stock fundamental data (valuation, ratings, company). */
    public StockInfoClient stockInfo() { return stockInfoClient; }

    /** Financial statements, dividends and earnings. */
    public FinancialClient financial() { return financialClient; }

    /** Quota for the current API key ({@code GET /package/info}). */
    public PackageClient packages() { return packageClient; }

    @Override
    public void close() {
        httpClient.close();
    }

    /**
     * Create a new InfowayClient builder.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for InfowayClient.
     */
    public static class Builder {

        private final HttpClient.Builder httpBuilder = HttpClient.builder();

        /**
         * Set the API key for authentication.
         *
         * @param apiKey your Infoway API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            httpBuilder.apiKey(apiKey);
            return this;
        }

        /**
         * Set a custom base URL (default: https://data.infoway.io).
         *
         * @param baseUrl base URL
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            httpBuilder.baseUrl(baseUrl);
            return this;
        }

        /**
         * Set the request timeout in seconds (default: 15).
         *
         * @param timeoutSeconds timeout in seconds
         * @return this builder
         */
        public Builder timeout(long timeoutSeconds) {
            httpBuilder.timeout(timeoutSeconds);
            return this;
        }

        /**
         * Set the maximum number of retries (default: 3).
         *
         * @param maxRetries max retries
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            httpBuilder.maxRetries(maxRetries);
            return this;
        }

        /**
         * Build the InfowayClient.
         *
         * @return configured InfowayClient instance
         */
        public InfowayClient build() {
            return new InfowayClient(httpBuilder.build());
        }
    }
}
