# Infoway Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.infoway/infoway-sdk.svg)](https://search.maven.org/artifact/io.infoway/infoway-sdk)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**English** | [中文](README_CN.md)

Official Java SDK for the [Infoway](https://infoway.io) real-time financial data API. Supports stocks (HK, US, CN, JP, KS, IN, TW), crypto, and common market data via REST and WebSocket.

Full walkthrough with copy-paste examples: [USAGE.md](USAGE.md) · [使用说明](USAGE_CN.md).

> **Upgrade to 0.3.0.** Earlier releases (including the 0.1.0 shown by older docs) mis-handled
> most error responses and sent wrong parameters on every `basic()` call. See
> [What changed in 0.3.0](#what-changed-in-030) and [0.2.0](#what-changed-in-020) before
> upgrading — the `basic()` signatures changed in 0.2.0.

## Installation

### Maven

```xml
<dependency>
    <groupId>io.infoway</groupId>
    <artifactId>infoway-sdk</artifactId>
    <version>0.3.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.infoway:infoway-sdk:0.3.0'
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

// Quota for the current key
JsonElement pkg = client.packages().getInfo();

// Clean up
client.close();
```

## REST API

### Market Data

Available for all markets: `client.stock()`, `client.crypto()`, `client.japan()`, `client.india()`, `client.korea()`, `client.taiwan()`, `client.common()`.

| Method | Description |
|--------|-------------|
| `getTrade(codes)` | Real-time trade data |
| `getDepth(codes)` | Order book depth |
| `getKline(codes, type, count)` | K-line / candlestick data |
| `getKline(codes, type, count, timestamp)` | Same, ending at a unix-seconds timestamp (minute/hour bars) |

### Basic Info

```java
// type is one of STOCK_US STOCK_CN STOCK_HK STOCK_JP STOCK_KS STOCK_IN STOCK_TW
// CRYPTO FOREX FUTURES ENERGY METAL INDICES
client.basic().getSymbols(SymbolType.STOCK_US);              // Symbol list
client.basic().getSymbols("STOCK_US", "AAPL.US,TSLA.US");    // …restricted to codes
client.basic().getSymbolInfo("STOCK_US", "AAPL.US");         // Symbol details
client.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US"); // Single-name profile
client.basic().getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815");
client.basic().getTradingDays("US", "20260801", "20260815"); // Trading calendar
client.basic().getTradingSchedule();                         // Sessions / holidays (no market filter)
client.basic().getTradingScheduleByType(ScheduleType.ENERGY); // ENERGY / FOREX / FUTURES / METAL / INDICES
client.basic().getMarkets();                                 // Per-market session table
client.packages().getInfo();                                 // Quota: packageName, expireTime, apiNumPerSec, …
```

Dates are `YYYYMMDD` strings. Every parameter above is required by the server: sending
`market=US` to `getSymbols` returns HTTP 400 `Required parameter 'type' is not present.`
`getTradingSchedule("US")` is the same as the no-arg call. Passing `SymbolType.STOCK_US`
to `getTradingScheduleByType` throws `IllegalArgumentException` before the request.

### Market Overview

```java
client.market().getTemperature(Lang.ZH_CN, Market.HK, Market.US);
client.market().getBreadth(Market.US, Lang.ZH_CN);
client.market().getTurnover(Market.US);
client.market().getIndexes(Lang.EN);
client.market().getLeaders(Market.US, 10);
client.market().getOverview(Market.US, Lang.ZH_CN);
client.market().getRankCategories(Market.US);
client.market().getRank(Market.US, "all", RankSort.CHG, SortOrder.DESC, 30, 0, Lang.EN);
```

### Plate (Sector)

```java
client.plate().getIndustry("HK", 200);     // Industry sectors
client.plate().getConcept("HK", 100);      // Concept sectors
client.plate().getMembers("IN20293.HK", 0, 50); // Sector members
client.plate().getIntro("IN20293.HK");     // Sector intro
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
client.stockInfo().getCompany("00700.HK", "zh-CN"); // Optional lang=en|zh-CN
```

### Financials

```java
client.financial().getEarningStatus("AAPL.US", SymbolType.STOCK_US);
client.financial().getIncomeStatement("AAPL.US", SymbolType.STOCK_US, PeriodType.FQ);
client.financial().getRevenue("AAPL.US", SymbolType.STOCK_US);
client.financial().getCashFlow("AAPL.US", SymbolType.STOCK_US, PeriodType.FY);
client.financial().getBalanceSheet("AAPL.US", SymbolType.STOCK_US);
client.financial().getStatistics("AAPL.US", SymbolType.STOCK_US);
client.financial().getDividend("00700.HK", SymbolType.STOCK_HK);
client.financial().getDividendPayout("AAPL.US", SymbolType.STOCK_US);
client.financial().getEarnings("AAPL.US", SymbolType.STOCK_US, PeriodType.FQ);
```

## WebSocket

```java
import io.infoway.sdk.InfowayWebSocket;

InfowayWebSocket ws = InfowayWebSocket.builder()
    .apiKey("YOUR_API_KEY")                // omit to read INFOWAY_API_KEY
    .business(WsBusiness.CRYPTO)           // STOCK | JAPAN | INDIA | KOREA | TAIWAN | CRYPTO | COMMON
    .printFrames(true)                     // optional; default false (DEBUG only)
    .onTrade(data -> System.out.println(data.get("s") + " " + data.get("p")))
    .onDepth(data -> System.out.println("Depth: " + data))
    .onKline(data -> System.out.println("Kline: " + data))
    .onError(err -> System.err.println("Error: " + err.getMessage()))
    .onReconnect(() -> System.out.println("Reconnected"))
    .build();

ws.connect();
ws.subscribeTrade("BTCUSDT,ETHUSDT");      // one frame, all symbols
ws.subscribeTrade("AAPL.US", true);        // includeTy=true adds equity trade-type `ty`
ws.subscribeKline("BTCUSDT", KlineType.MIN_1);

// Later...
ws.unsubscribeKline("BTCUSDT", KlineType.MIN_1);
ws.close();
```

Callbacks receive the **`data` payload**, matching what the REST layer returns — trade pushes
arrive as `{"s","p","v","vw","t","td"}`, not wrapped in `{"code":10002,"data":{…}}`.

Things the server does that will otherwise look like SDK bugs:

| Behaviour | What it means |
|-----------|---------------|
| A plain-text first frame `You have permission to subscribe to all market data` | Sent on `business=stock`. The SDK skips non-JSON frames. |
| `{"code":200,"msg":"ws connect success"}` | Welcome frame, not an error. |
| `{"code":10001,"msg":"ok"}` then silence | An ack only means *accepted*. A wrong `business` (e.g. subscribing `AAPL.US` on `crypto`), an unknown symbol, or a closed market all ack and then push nothing. Pick the `business` that matches the market. |
| Heartbeats are never answered | The server sends no reply to `10010`. Never treat a missing ack as a dead connection. |
| Connection dropped after many frames | The limit is **60 frames/minute/connection** (subscribes + unsubscribes + heartbeats). Always merge symbols into one comma-separated `codes` string. |
| HTTP 401 on connect | Wrong key, or the key has no access to that channel. The SDK raises `InfowayAuthException` and **stops reconnecting** instead of hammering the gateway. |
| `onReconnect` on the first open | It fires only after a later successful open. `onDisconnect` is unexpected drop only — `close()` does not fire it and cancels backoff. |
| A topic comes back after unsubscribe | The client keeps the **desired** set. Unsubscribe is not replayed; one kline interval can be dropped without clearing the others. |

### News

News is a separate endpoint (`wss://data.infoway.io/news`) with its own entitlement:

```java
import io.infoway.sdk.InfowayNewsWebSocket;

InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
    .apiKey("YOUR_API_KEY")
    .lang(NewsLang.EN)                     // EN ZH_HANS ZH_HANT JA KO …
    .printFrames(true)                     // optional; default false
    .onNews(item -> System.out.println(item.get("title").getAsString()))
    .onNewsParsed(item -> System.out.println(item.title()))
    .onError(err -> System.err.println(err.getMessage()))
    .build();

news.connect();
news.unsubscribe();                        // code 11020
// later
news.close();
```

Push fields: `dk` (dedup key), `country`, `lang`, `route`, `title`, `published` (epoch **seconds**),
`urgency` (lower = more urgent), `provider`, `symbols[]`, `link`, `content`, `sd` (summary).
Subscribing again replaces the language; `unsubscribe()` sends code 11020. One connection per key.
A key without the news entitlement is rejected with HTTP 401 during the handshake.

### Typed models (optional)

Raw `JsonElement` stays the default. When you want the server's quirks absorbed:

```java
import io.infoway.sdk.model.*;

List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
List<Depth> books  = client.crypto().getDepthParsed("BTCUSDT");
List<Kline> bars   = client.crypto().getKlineParsed("BTCUSDT", KlineType.MIN_1, 100);

ws.setOnKline(data -> handle(Normalizer.kline(data)));
```

| On the wire | Typed model |
|-------------|-------------|
| prices/volumes as strings (`"305.771"`) | `BigDecimal` |
| `t`: number of **milliseconds** (trade/depth) vs string of **seconds** (kline) | `Instant`, decided per field |
| change percent: `pc` (REST) / `pfr` (WS), value `"0.03%"` | `changePercent` = `0.0003` |
| kline nested in `respList` | flattened list |
| depth `a`/`b` as transposed columns `[[price…],[qty…]]` | `List<DepthLevel>` of `(price, quantity)` |
| `vw` — the traded **amount**, not a VWAP | `turnover` |

### WebSocket Codes

Client → server:

| Code | Name | Description |
|------|------|-------------|
| 10000 | SUB_TRADE | Subscribe trade |
| 10003 | SUB_DEPTH | Subscribe depth |
| 10006 | SUB_KLINE | Subscribe kline (payload `data.arr=[{codes, type}]`) |
| 10010 | HEARTBEAT | Heartbeat keepalive (server replies 10011 only if client sends `ack=1`) |
| 11000 | UNSUB_TRADE | Unsubscribe trade |
| 11001 | UNSUB_DEPTH | Unsubscribe depth |
| 11002 | UNSUB_KLINE | Unsubscribe kline |
| 10020 | SUB_NEWS | Subscribe news (on the `/news` endpoint) |
| 11020 | UNSUB_NEWS | Unsubscribe news |

Server → client:

| Code | Name | Description |
|------|------|-------------|
| 10001 | SUB_TRADE_ACK | Trade subscribe acknowledgement |
| 10002 | PUSH_TRADE | **Real-time trade push** |
| 10004 | SUB_DEPTH_ACK | Depth subscribe acknowledgement |
| 10005 | PUSH_DEPTH | **Real-time depth push** |
| 10007 | SUB_KLINE_ACK | Kline subscribe acknowledgement |
| 10008 | PUSH_KLINE | **Real-time kline push** |
| 10011 | HEART_APPLY | Heartbeat ack (only when the client sent `ack=1`) |
| 11010 | UNSUB_ACK | Unsubscribe acknowledgement (trade / depth / kline / news) |
| 10021 | SUB_NEWS_ACK | News subscribe acknowledgement |
| 10022 | PUSH_NEWS | **Real-time news push** |
| 200 | — | Welcome frame `{"code":200,"msg":"ws connect success"}` |

Server error frames (`{"code":5xx,"msg":"...","traceId":"..."}`) go to `onError`. The stock service sometimes prefixes them with `Subscribe fail:`; the SDK strips that.

| Code | Name | Description |
|------|------|-------------|
| 500 | SERVER_ERROR | Internal error |
| 501 / 502 | Rate limit | Raised as `InfowayRateLimitException` (501 = 60 frames/min) |
| 505 / 516 | Product quota | Per-connection / all connections for the key |
| 506 / 507 | PARAM_ERROR / PARAM_LOST | Bad or missing fields |
| 508–511 | API key | Expired / invalid / empty / blacklisted |
| 512 / 513 / 514 | Conn / heartbeat / URL | 513 is followed by a server close |
| 515 | PARAM_NOT_JSON | Inbound text was not JSON |
| 517–521 | Handshake | Missing key / no entitlement; 519/520 differ on korea, taiwan, news |

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
    // 401 Unauthorized (or ret=401)
    System.err.println("Auth failed: " + e.getMsg());
} catch (InfowayRateLimitException e) {
    // HTTP 429, REST ret 501/502, or HTTP 200 with {"detail":"Rate limit exceeded"}
    System.err.println("Rate limited [" + e.getRet() + " " + e.getErrorName() + "]: " + e.getMsg());
} catch (InfowayApiException e) {
    // getErrorName() is RestErrorCode on HTTP, WsErrorCode on sockets (508–514 differ)
    System.err.println(e.getMessage());
    System.err.println("Trace ID: " + e.getTraceId());
} catch (InfowayTimeoutException e) {
    // Request timeout
    System.err.println("Timeout: " + e.getMessage());
} catch (InfowayIoException e) {
    // Retries exhausted (network / I/O)
    System.err.println("IO: " + e.getMessage());
}
```

`InfowayRateLimitException` extends `InfowayApiException`, so existing catch blocks keep working.
`getMessage()` looks like `[508 PRODUCT_NOT_EXISTS] All product not exists (trace: …)` on REST
and `[508 APIKEY_EXPIRED] …` on WebSocket — same number, different enum (`RestErrorCode` vs
`WsErrorCode`). Do not decode REST `ret` with `WsErrorCode`. REST limits: **1200 requests/minute/key**;
K-lines are capped at 500 bars per symbol, and a multi-symbol `getKline` is silently truncated to 2
bars per symbol by the server. A forged key is `InfowayAuthException` `[401] Token invalid` on REST
and handshake HTTP 401 (no reconnect) on both sockets.

REST `ret` (`RestErrorCode`): 200 success; 400 commonApi bad params; 500 uncaught **or** production
quote errors that still ship as 500 + English `msg`; 501/502 rate limit; 503 too many bars; 505 too
many symbols; 506/507 bad/missing field; **508 `PRODUCT_NOT_EXISTS`**; 509 token permission expired;
513 kline history older than the package; **514 `NO_PERMISSION`**. `/common/basic/*` only uses
200 / 400 / 500. Production quote errors (2026-09-21) are still HTTP 200 + `ret=500` with the
enum text in `msg`.

## What changed in 0.3.0

- Current Maven coordinate is `io.infoway:infoway-sdk:0.3.0`.
- `packages().getInfo()`, `getTradingScheduleByType(ScheduleType)`, `InfowayIoException`.
- `RestErrorCode` / `WsErrorCode` / `getErrorName()`; REST 508 is `PRODUCT_NOT_EXISTS`, WS 508 is `APIKEY_EXPIRED`.
- Typed params: `Market`, `Lang`, `NewsLang`, `PeriodType`, `WsBusiness`, `RankSort`, `SortOrder`, `ScheduleType`.
- `printFrames` defaults to false (DEBUG only). REST and WebSocket both read `INFOWAY_API_KEY`.
- News `onNewsParsed`. Korea / Taiwan / financial clients.

## What changed in 0.2.0

**Fixes**

- Error responses are no longer swallowed. Before, anything without a `ret`/`code` field —
  HTTP 400 problem+json, HTTP 404, HTTP 429, `{"detail":"Rate limit exceeded"}` — was treated as
  success and returned `null`. Gateway HTML pages threw `JsonSyntaxException` and empty 502 bodies
  threw `NullPointerException`.
- Responses without a `data` key (e.g. `plate().getIntro(...)`) return the whole body instead of `null`.
- WebSocket callbacks receive `data`, aligned with REST. `msg.get("p")` used to be `null`.
- `unsubscribeKline` now sends `klineTypes`; without it the server dropped **all** intervals of that symbol.
- Subscriptions issued before the socket is open are no longer discarded silently.
- An invalid key now raises `InfowayAuthException` and stops reconnecting instead of retrying forever.
- Server error frames (e.g. `506`/`507`) reach `onError` instead of being logged at debug and dropped.

**Breaking changes** — every `basic()` method used to send parameters the server rejects and
returned `null` 100% of the time:

| Before | Now |
|--------|-----|
| `getSymbols("US")` | `getSymbols("STOCK_US")` / `getSymbols(SymbolType.STOCK_US)` |
| `getSymbolInfo("AAPL.US")` | `getSymbolInfo("STOCK_US", "AAPL.US")` |
| `getAdjustmentFactors("AAPL.US")` | `getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815")` |
| `getTradingDays("US")` | `getTradingDays("US", "20260801", "20260815")` |
| `getTradingHours("US")` | `getTradingSchedule("US")` (old name kept as a deprecated alias) |

**New**

- `InfowayNewsWebSocket` — the `/news` channel (`onNews` / `onNewsParsed`).
- `io.infoway.sdk.model` — typed `Trade`/`Depth`/`Kline`/`NewsItem` plus `Normalizer`, and
  `getTradeParsed` / `getDepthParsed` / `getKlineParsed` on every market client.
- `InfowayRateLimitException`, `SymbolType`. String overloads still work.

## Requirements

- Java 21+
- Dependencies: OkHttp 4.x, Gson, SLF4J

## Resources

- Website: [https://infoway.io](https://infoway.io)
- API Docs: [https://docs.infoway.io](https://docs.infoway.io)
- Free Trial: [7-day free trial](https://infoway.io)

## License

MIT
