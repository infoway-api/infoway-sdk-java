# Infoway Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.infoway/infoway-sdk.svg)](https://search.maven.org/artifact/io.infoway/infoway-sdk)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**English** | [中文](README_CN.md)

Official Java SDK for the [Infoway](https://infoway.io) real-time financial data API. Supports stocks (HK, US, CN), crypto, Japan, and India markets via REST and WebSocket.

## Installation

### Maven

```xml
<dependency>
    <groupId>io.infoway</groupId>
    <artifactId>infoway-sdk</artifactId>
    <version>0.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.infoway:infoway-sdk:0.1.0'
```

## Quick Start

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.KlineType;
import com.google.gson.JsonElement;

// Create client
InfowayClient client = InfowayClient.builder()
    .apiKey("YOUR_API_KEY")
    .build();

// Real-time trade data
JsonElement trades = client.stock().getTrade("AAPL.US");
System.out.println(trades);

// K-line data
JsonElement klines = client.crypto().getKline("BTCUSDT", KlineType.DAY, 100);

// Market temperature
JsonElement temp = client.market().getTemperature("HK,US");

// Plate (sector) data
JsonElement industry = client.plate().getIndustry("HK", 10);

// Stock info
JsonElement company = client.stockInfo().getCompany("AAPL.US");

// Clean up
client.close();
```

## REST API

### Market Data

Available for all markets: `client.stock()`, `client.crypto()`, `client.japan()`, `client.india()`, `client.common()`.

| Method | Description |
|--------|-------------|
| `getTrade(codes)` | Real-time trade data |
| `getDepth(codes)` | Order book depth |
| `getKline(codes, type, count)` | K-line / candlestick data |

### Basic Info

```java
client.basic().getSymbols("US");           // Symbol list
client.basic().getSymbolInfo("AAPL.US");   // Symbol details
client.basic().getTradingDays("US");       // Trading calendar
client.basic().getTradingHours("US");      // Trading hours
client.basic().getAdjustmentFactors("AAPL.US"); // Adjustment factors
```

### Market Overview

```java
client.market().getTemperature("HK,US");   // Market sentiment
client.market().getBreadth("US");          // Advance/decline
client.market().getIndexes();              // Major indexes
client.market().getLeaders("US", 10);      // Top movers
client.market().getRankConfig("US");       // Rank config
```

### Plate (Sector)

```java
client.plate().getIndustry("HK", 200);     // Industry sectors
client.plate().getConcept("HK", 100);      // Concept sectors
client.plate().getMembers("BK1001", 0, 50); // Sector members
client.plate().getIntro("BK1001");         // Sector intro
client.plate().getChart("HK", 50);         // Sector chart
```

### Stock Info

```java
client.stockInfo().getValuation("AAPL.US"); // Valuation
client.stockInfo().getRatings("AAPL.US");   // Analyst ratings
client.stockInfo().getCompany("AAPL.US");   // Company info
client.stockInfo().getPanorama("AAPL.US");   // Overview
client.stockInfo().getConcepts("AAPL.US");   // Concepts
client.stockInfo().getEvents("AAPL.US", 20); // Events
client.stockInfo().getDrivers("AAPL.US");    // Drivers
```

## WebSocket

```java
import io.infoway.sdk.InfowayWebSocket;

InfowayWebSocket ws = InfowayWebSocket.builder()
    .apiKey("YOUR_API_KEY")
    .business("stock")
    .onTrade(msg -> System.out.println("Trade: " + msg))
    .onDepth(msg -> System.out.println("Depth: " + msg))
    .onKline(msg -> System.out.println("Kline: " + msg))
    .onError(err -> System.err.println("Error: " + err.getMessage()))
    .onReconnect(() -> System.out.println("Reconnected"))
    .build();

ws.connect();
ws.subscribeTrade("AAPL.US,TSLA.US");
ws.subscribeDepth("AAPL.US");

// Later...
ws.unsubscribeTrade("TSLA.US");
ws.close();
```

### WebSocket Codes

| Code | Name | Description |
|------|------|-------------|
| 10000 | SUB_TRADE | Subscribe trade |
| 10001 | PUSH_TRADE | Trade push |
| 10002 | UNSUB_TRADE | Unsubscribe trade |
| 10003 | SUB_DEPTH | Subscribe depth |
| 10004 | PUSH_DEPTH | Depth push |
| 10005 | UNSUB_DEPTH | Unsubscribe depth |
| 10006 | SUB_KLINE | Subscribe kline |
| 10007 | PUSH_KLINE | Kline push |
| 10008 | UNSUB_KLINE | Unsubscribe kline |
| 10010 | HEARTBEAT | Heartbeat |

### K-line Types

| Enum | Value | Description |
|------|-------|-------------|
| MIN_1 | 1 | 1 minute |
| MIN_5 | 2 | 5 minutes |
| MIN_15 | 3 | 15 minutes |
| MIN_30 | 4 | 30 minutes |
| HOUR_1 | 5 | 1 hour |
| HOUR_2 | 6 | 2 hours |
| HOUR_4 | 7 | 4 hours |
| DAY | 8 | Daily |
| WEEK | 9 | Weekly |
| MONTH | 10 | Monthly |
| QUARTER | 11 | Quarterly |
| YEAR | 12 | Yearly |

## Configuration

| Builder Method | Default | Description |
|---------------|---------|-------------|
| `apiKey(key)` | `INFOWAY_API_KEY` env | API key |
| `baseUrl(url)` | `https://data.infoway.io` | Base URL |
| `timeout(secs)` | `15` | Request timeout (seconds) |
| `maxRetries(n)` | `3` | Max retries |

## Error Handling

```java
import io.infoway.sdk.exception.*;

try {
    client.stock().getTrade("INVALID");
} catch (InfowayAuthException e) {
    // 401 Unauthorized
    System.err.println("Auth failed: " + e.getMsg());
} catch (InfowayApiException e) {
    // Other API errors
    System.err.println("API error [" + e.getRet() + "]: " + e.getMsg());
    System.err.println("Trace ID: " + e.getTraceId());
} catch (InfowayTimeoutException e) {
    // Request timeout
    System.err.println("Timeout: " + e.getMessage());
}
```

## Requirements

- Java 17+
- Dependencies: OkHttp 4.x, Gson, SLF4J

## Resources

- Website: [https://infoway.io](https://infoway.io)
- API Docs: [https://docs.infoway.io](https://docs.infoway.io)
- Free Trial: [7-day free trial](https://infoway.io)

## License

MIT
