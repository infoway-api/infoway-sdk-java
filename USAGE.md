# Infoway Java SDK usage

Java 21+ guide for quotes, fundamentals and live sockets. Install notes and changelog live in [README.md](README.md). Official API reference: [docs.infoway.io](https://docs.infoway.io). Chinese twin: [USAGE_CN.md](USAGE_CN.md).

- Artifact: `io.infoway:infoway-sdk:0.3.0`
- REST: `https://data.infoway.io`
- Quotes WebSocket: `wss://data.infoway.io/ws`
- News WebSocket: `wss://data.infoway.io/news`

---

## Contents

1. [Install and client](#1-install-and-client)
2. [Symbol conventions](#2-symbol-conventions)
3. [REST: market data](#3-rest-market-data)
4. [REST: basics](#4-rest-basics)
5. [REST: market overview](#5-rest-market-overview)
6. [REST: plates](#6-rest-plates)
7. [REST: stock info](#7-rest-stock-info)
8. [REST: financials](#8-rest-financials)
9. [Typed models](#9-typed-models)
10. [WebSocket: quotes](#10-websocket-quotes)
11. [WebSocket: news](#11-websocket-news)
12. [Errors and limits](#12-errors-and-limits)
13. [Full example](#13-full-example)

---

## 1. Install and client

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

If you omit `apiKey()`, both REST and WebSocket read `INFOWAY_API_KEY`. `InfowayClient` is `Closeable`.

```java
import io.infoway.sdk.InfowayClient;

public class CreateClient {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder()
                .apiKey(System.getenv("INFOWAY_API_KEY"))
                .baseUrl("https://data.infoway.io") // optional
                .timeout(15)                        // seconds, default 15
                .maxRetries(3)                      // default 3, exponential backoff
                .build()) {
            System.out.println(client.crypto().getTrade("BTCUSDT"));
        }
    }
}
```

| Builder | Default | Meaning |
|---------|---------|---------|
| `apiKey(key)` | `INFOWAY_API_KEY` | API key |
| `baseUrl(url)` | `https://data.infoway.io` | REST root |
| `timeout(secs)` | `15` | Per-request timeout |
| `maxRetries(n)` | `3` | Retries |

| Entry | Use |
|-------|-----|
| `stock()` / `crypto()` / `japan()` / `india()` / `korea()` / `taiwan()` / `common()` | Trade, depth, kline |
| `basic()` | Symbols, calendar, single-name profile |
| `packages()` | Quota for the current key |
| `market()` | Sentiment, breadth, turnover, ranks |
| `plate()` | Industry / concept sectors |
| `stockInfo()` | Valuation, ratings, company |
| `financial()` | Statements, dividends, earnings |

String overloads still work. Prefer enums so you cannot send a value the server rejects:

| Enum | Wire values | Used by |
|------|-------------|---------|
| `KlineType` | `MIN_1`…`YEAR` (1–12) | K-line REST / WS |
| `SymbolType` | `STOCK_US` / `STOCK_CN` / `CRYPTO`… | `basic()` / `financial()` / stock detail |
| `Market` | `HK` `US` `CN` `JP` `KS` `TW` `IN` | Overview, plates, calendar; `Market.join` or varargs |
| `Lang` | `EN`=`en`, `ZH_CN`=`zh-CN` | REST `lang` |
| `NewsLang` | `EN` `ZH_HANS` `ZH_HANT` `JA` `KO`… | News WS |
| `PeriodType` | `FQ` / `FY` / `FH` | Financials |
| `WsBusiness` | `STOCK` `CRYPTO` `KOREA` `TAIWAN`… | Quote WS `business` |
| `RankSort` | `CHG` `LAST_DONE` `VOLUME`… | Rank `sort` |
| `SortOrder` | `ASC` `DESC` | Rank `order` |
| `ScheduleType` | `ENERGY` `FOREX` `FUTURES` `METAL` `INDICES` | Trading-schedule filter |
| `WsCode` / `WsErrorCode` / `RestErrorCode` | Protocol / WS 5xx / REST `ret` | Frames and errors |

---

## 2. Symbol conventions

| Market | Form | Good | Bad |
|--------|------|------|-----|
| US | `.US` | `AAPL.US` | `AAPL` |
| HK | `.HK`, 5-digit pad | `00700.HK` | `700.HK` |
| Shanghai | `.SH` | `600519.SH` | `600519.CN` |
| Shenzhen | `.SZ` | `000001.SZ` | `000001.CN` |
| Japan | `.JP` | `7203.JP` | |
| Korea | `.KS` | `005930.KS` | |
| India | `.IN` | `RELIANCE.IN` | |
| Taiwan | `.TW` | `2330.TW` | |
| Crypto | pair | `BTCUSDT` | |
| FX / metals | pair | `USDJPY` | |

`basic()` / `financial()` / `getStockDetail()` take a **product type**, not a market code `US`:

`STOCK_US` `STOCK_CN` `STOCK_HK` `STOCK_JP` `STOCK_KS` `STOCK_IN` `STOCK_TW`  
`CRYPTO` `FOREX` `FUTURES` `ENERGY` `METAL` `INDICES`

`getSymbols` with `market=US` returns HTTP 400 `Required parameter 'type' is not present.`

---

## 3. REST: market data

Seven market clients share the same methods; only the path prefix changes. Join codes with commas.

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.KlineType;

public class MarketDataExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            JsonElement us = client.stock().getTrade("AAPL.US,TSLA.US");
            JsonElement btc = client.crypto().getTrade("BTCUSDT");
            JsonElement kr = client.korea().getTrade("005930.KS");
            JsonElement tw = client.taiwan().getTrade("2330.TW");

            JsonObject tick = btc.getAsJsonArray().get(0).getAsJsonObject();
            System.out.println(tick.get("s").getAsString() + " " + tick.get("p").getAsString());

            JsonElement depth = client.crypto().getDepth("BTCUSDT");

            JsonElement latest = client.crypto().getKline("BTCUSDT", KlineType.MIN_1, 100);
            long endSeconds = 1_700_000_000L;
            JsonElement historical = client.crypto()
                    .getKline("BTCUSDT", KlineType.MIN_1, 100, endSeconds);

            printFirstBar(latest);
        }
    }

    private static void printFirstBar(JsonElement data) {
        JsonObject entry = data.getAsJsonArray().get(0).getAsJsonObject();
        JsonArray bars = entry.getAsJsonArray("respList");
        JsonObject bar = bars.get(0).getAsJsonObject();
        System.out.printf("t=%s o=%s h=%s l=%s c=%s%n",
                bar.get("t"), bar.get("o"), bar.get("h"), bar.get("l"), bar.get("c"));
    }
}
```

Trade fields: `s` symbol, `p` price, `v` size, `vw` turnover, `t` milliseconds, `td` side (0 default / 1 buy / 2 sell).  
K-lines are wrapped per symbol; bars sit in `respList`; `t` is a **seconds** string. `timestamp` only applies to minute / hour bars.

| Enum | Value | Interval |
|------|-------|----------|
| `MIN_1` / `MIN_5` / `MIN_15` / `MIN_30` | 1–4 | Minutes |
| `HOUR_1` / `HOUR_2` / `HOUR_4` | 5–7 | Hours |
| `DAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` | 8–12 | Day and above |

At most **500** bars per symbol. Multi-symbol kline requests are capped at 2 bars each.

SDK always uses `POST /{market}/v2/batch_kline`.

---

## 4. REST: basics

Dates are always `YYYYMMDD`.

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.Market;
import io.infoway.sdk.ScheduleType;
import io.infoway.sdk.SymbolType;

public class BasicExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            client.basic().getSymbols(SymbolType.STOCK_US);
            client.basic().getSymbols(SymbolType.STOCK_TW, "2330.TW");
            client.basic().getSymbolInfo(SymbolType.STOCK_US, "AAPL.US");
            client.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US");
            client.basic().getAdjustmentFactors("AAPL.US", Market.US, "20260801", "20260815");
            client.basic().getTradingDays(Market.US, "20260801", "20260815");
            client.basic().getTradingSchedule();
            client.basic().getTradingScheduleByType(ScheduleType.ENERGY);
            client.basic().getMarkets();
            System.out.println(client.packages().getInfo());
        }
    }
}
```

`getTradingHours` is deprecated — use `getTradingSchedule`. The schedule endpoint has **no market filter**; `getTradingSchedule("US")` is the same as the no-arg call. Filter by product with `ScheduleType` (`ENERGY` / `FOREX` / `FUTURES` / `METAL` / `INDICES`). Passing `SymbolType.STOCK_US` throws `IllegalArgumentException` before the request.

`packages().getInfo()` is `GET /package/info`: `packageName`, `expireTime`, `apiNumPerSec`, `maxWsConNum`, `maxNum`, `maxYearHisData`, `allWsNum`.

---

## 5. REST: market overview

Use `Market` and `Lang`. Temperature accepts several markets at once.

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.Lang;
import io.infoway.sdk.Market;
import io.infoway.sdk.RankSort;
import io.infoway.sdk.SortOrder;

public class MarketOverviewExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            client.market().getTemperature(Lang.ZH_CN, Market.HK, Market.US);
            client.market().getBreadth(Market.US, Lang.ZH_CN);
            client.market().getTurnover(Market.US);
            client.market().getIndexes(Lang.EN);
            client.market().getLeaders(Market.US, 10);
            client.market().getOverview(Market.US, Lang.ZH_CN);
            client.market().getRankCategories(Market.US);
            client.market().getRank(Market.US, "all", RankSort.CHG, SortOrder.DESC, 30, 0, Lang.EN);
        }
    }
}
```

Rank `key` values come from `getRankCategories`. `getRankConfig` is deprecated (HTTP 404).

---

## 6. REST: plates

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.Market;

public class PlateExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            client.plate().getIndustry(Market.HK, 200);
            client.plate().getConcept("HK", 100);
            client.plate().getMembers("IN20293.HK", 0, 50);
            client.plate().getIntro("IN20293.HK");
            client.plate().getChart("HK", 50);
        }
    }
}
```

---

## 7. REST: stock info

Optional `lang`: `en` or `zh-CN`.

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.Lang;

public class StockInfoExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            String symbol = "AAPL.US";
            client.stockInfo().getValuation(symbol);
            client.stockInfo().getRatings(symbol);
            client.stockInfo().getCompany(symbol, Lang.ZH_CN);
            client.stockInfo().getPanorama(symbol);
            client.stockInfo().getConcepts(symbol);
            client.stockInfo().getEvents(symbol, 20);
            client.stockInfo().getDrivers(symbol);
        }
    }
}
```

---

## 8. REST: financials

Every method needs `symbol` plus `type`. Statement-style methods accept `period_type`:

| Value | Meaning |
|-------|---------|
| `fq` | Quarter |
| `fy` | Year |
| `fh` | Half-year |

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.PeriodType;
import io.infoway.sdk.SymbolType;

public class FinancialExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            String symbol = "AAPL.US";
            SymbolType type = SymbolType.STOCK_US;

            client.financial().getEarningStatus(symbol, type);
            client.financial().getIncomeStatement(symbol, type, PeriodType.FQ);
            client.financial().getRevenue(symbol, type);
            client.financial().getCashFlow(symbol, type, PeriodType.FY);
            client.financial().getBalanceSheet(symbol, type);
            client.financial().getStatistics(symbol, type);
            client.financial().getDividend(symbol, type);
            client.financial().getDividendPayout(symbol, type);
            client.financial().getEarnings(symbol, type, PeriodType.FQ);
        }
    }
}
```

HK example: `client.financial().getDividend("00700.HK", SymbolType.STOCK_HK)`.

Production is lenient on a few financial filters: `type=US` still returns rows for `AAPL.US` (suffix wins); `period_type=xx` returns HTTP 200 and an empty list. `getStockDetail(..., CRYPTO)` returns `data: null` with HTTP 200.

---

## 9. Typed models

Default return is Gson `JsonElement`. Use `*Parsed` or `Normalizer` when you want quirks absorbed.

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.KlineType;
import io.infoway.sdk.model.Depth;
import io.infoway.sdk.model.Kline;
import io.infoway.sdk.model.Trade;

import java.util.List;

public class ParsedExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
            List<Depth> books = client.crypto().getDepthParsed("BTCUSDT");
            List<Kline> bars = client.crypto().getKlineParsed("BTCUSDT", KlineType.MIN_1, 20);

            Trade t = trades.get(0);
            System.out.println(t.symbol() + " " + t.price() + " " + t.time());

            Kline bar = bars.get(0);
            System.out.println(bar.open() + " -> " + bar.close() + " chg=" + bar.changePercent());
        }
    }
}
```

| On the wire | After normalize |
|-------------|-----------------|
| Prices / sizes as `"305.771"` | `BigDecimal` |
| Trade / depth `t` milliseconds; kline `t` seconds string | `Instant` |
| REST `pc` / WS `pfr` = `"0.03%"` | `changePercent = 0.0003` |
| K-lines nested in `respList` | `List<Kline>` |
| Depth `a`/`b` = `[[prices…],[qtys…]]` | `List<DepthLevel>` |
| `vw` is turnover, not VWAP | `turnover` |

---

## 10. WebSocket: quotes

Separate from the REST client. `business` must match the market; a wrong channel acks and then pushes nothing.

`printFrames` defaults to **false**. Frames are DEBUG only unless you turn printing on or use `onFrame`. REST and WebSocket both fall back to `INFOWAY_API_KEY`.

```java
import io.infoway.sdk.InfowayWebSocket;
import io.infoway.sdk.KlineType;
import io.infoway.sdk.WsBusiness;
import io.infoway.sdk.model.Normalizer;

public class QuoteSocketExample {
    public static void main(String[] args) throws InterruptedException {
        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(System.getenv("INFOWAY_API_KEY"))
                .business(WsBusiness.CRYPTO) // STOCK / JAPAN / INDIA / KOREA / TAIWAN / CRYPTO / COMMON
                .printFrames(true)           // optional; default false
                .onFrame(System.out::println)
                .onTrade(data -> System.out.println(
                        "TRADE " + data.get("s").getAsString() + " " + data.get("p").getAsString()))
                .onDepth(data -> System.out.println("DEPTH " + data.get("s")))
                .onKline(data -> System.out.println("KLINE " + Normalizer.kline(data)))
                .onError(err -> System.err.println(err.getMessage()))
                .onReconnect(() -> System.out.println("reconnected"))
                .onDisconnect(() -> System.out.println("disconnected"))
                .build();

        ws.connect();
        ws.subscribeTrade("BTCUSDT,ETHUSDT");
        ws.subscribeDepth("BTCUSDT,ETHUSDT");
        ws.subscribeKline("BTCUSDT", KlineType.MIN_1);

        Thread.sleep(30_000);

        ws.unsubscribeKline("BTCUSDT", KlineType.MIN_1);
        ws.unsubscribeTrade("BTCUSDT,ETHUSDT");
        ws.close();
    }
}
```

Equity trade types (odd lots, auctions, …) need `includeTy=true`. Crypto still omits `ty`.

```java
InfowayWebSocket stock = InfowayWebSocket.builder()
        .business(WsBusiness.STOCK)
        .onTrade(data -> System.out.println(data.get("s") + " ty=" + data.get("ty")))
        .build();
stock.connect();
stock.subscribeTrade("AAPL.US,TSLA.US", true);
```

Callbacks receive **`data`**, not `{"code":10002,"data":{...}}`. Subscribe before `connect()`; the client replays on open.

Lifecycle (reconnect / subscribe / close):

- Heartbeat `10010` every 30s, **one timer per live session**. A drop schedules **at most one** reconnect (1s → 30s backoff).
- `onReconnect` fires only after a later successful open, never on the first `connect()`.
- `onDisconnect` fires on an unexpected drop. `close()` does **not** fire it and does **not** reconnect; it also cancels a pending backoff so close returns immediately.
- The client keeps the **desired** subscription set. Unsubscribe is forgotten and is **not** replayed. Subscribe while reconnecting is flushed on the next open. Unsubscribing one kline interval leaves the others.
- HTTP 401 still stops reconnecting.

| Symptom | Cause |
|---------|-------|
| First frame is plain text `You have permission...` | `business=stock` greeting; SDK skips it |
| `{"code":200,"msg":"ws connect success"}` | Welcome, not an error |
| ack `ok` then silence | Wrong business, unknown code, or closed market |
| No heartbeat reply | Server does not answer `10010`; do not reconnect on missing ack |
| Dropped after many frames | **60 frames/minute/connection** (sub + unsub + heartbeat); merge codes |
| HTTP 401 | Bad key or no channel entitlement; `InfowayAuthException`, no reconnect |

Protocol:

| Dir | Code | Meaning |
|-----|------|---------|
| out | 10000 / 10003 / 10006 | Subscribe trade / depth / kline |
| out | 11000 / 11001 / 11002 | Unsubscribe |
| out | 10010 | Heartbeat (30s; `ack=1` yields 10011) |
| in | 10001 / 10004 / 10007 | Subscribe ack |
| in | 10002 / 10005 / 10008 | Push |
| in | 10011 | Heartbeat ack (optional) |
| in | 11010 | Unsubscribe ack (quotes + news) |
| in | 200 | Welcome |
| in | 500–521 | Server error → `onError` (501/502 rate limit; see `WsErrorCode`) |

---

## 11. WebSocket: news

`wss://data.infoway.io/news`, separate entitlement. One news connection per key.

```java
import io.infoway.sdk.InfowayNewsWebSocket;
import io.infoway.sdk.NewsLang;

public class NewsSocketExample {
    public static void main(String[] args) throws InterruptedException {
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .lang(NewsLang.ZH_HANS)
                .printFrames(true)
                .onNews(item -> System.out.printf("%s %s%n",
                        item.get("title").getAsString(),
                        item.get("sd").getAsString()))
                .onNewsParsed(item -> System.out.println(item.title()))
                .onError(err -> System.err.println(err.getMessage()))
                .build();

        news.connect();
        Thread.sleep(60_000);

        news.unsubscribe(); // 11020
        news.subscribe(NewsLang.EN); // replaces language
        news.close();
    }
}
```

Push fields: `dk` (dedup), `country`, `lang`, `route`, `title`, `published` (**seconds**), `urgency` (lower = hotter), `provider`, `symbols[]`, `link`, `content`, `sd` (summary).

| Dir | Code | Meaning |
|-----|------|---------|
| out | 10020 / 11020 | Subscribe / unsubscribe |
| in | 10021 / 10022 | Ack / push |

A key without news access fails the handshake with HTTP 401. The current `lang` is replayed on reconnect; `unsubscribe()` clears it so reconnect will not resubscribe. `onReconnect` / `onDisconnect` follow the same rules as the quotes socket.

---

## 12. Errors and limits

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayAuthException;
import io.infoway.sdk.exception.InfowayIoException;
import io.infoway.sdk.exception.InfowayRateLimitException;
import io.infoway.sdk.exception.InfowayTimeoutException;

public class ErrorHandlingExample {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder().build()) {
            client.stock().getTrade("INVALID");
        } catch (InfowayAuthException e) {
            System.err.println("auth: " + e.getMsg());
        } catch (InfowayRateLimitException e) {
            System.err.printf("rate [%d %s] %s%n", e.getRet(), e.getErrorName(), e.getMsg());
        } catch (InfowayTimeoutException e) {
            System.err.println("timeout: " + e.getMessage());
        } catch (InfowayIoException e) {
            System.err.println("io: " + e.getMessage());
        } catch (InfowayApiException e) {
            // HTTP → RestErrorCode; WS → WsErrorCode. 508–514 collide.
            System.err.println(e.getMessage());
            System.err.printf("ret=%d name=%s msg=%s trace=%s%n",
                    e.getRet(), e.getErrorName(), e.getMsg(), e.getTraceId());
        }
    }
}
```

`InfowayRateLimitException` extends `InfowayApiException`. REST budget is about **1200 calls/minute/key**. HTTP 429, REST `ret` 501/502, or `{"detail":"Rate limit exceeded"}` are retried with backoff.

`getMessage()` includes the code and enum name, e.g. REST `[508 PRODUCT_NOT_EXISTS] All product not exists` vs WebSocket `[508 APIKEY_EXPIRED] …`. Do not decode REST `ret` with `WsErrorCode`.

A forged key is `InfowayAuthException` `[401] Token invalid` on REST, and handshake HTTP 401 (no reconnect) on both sockets.

### REST `ret` (`RestErrorCode`)

| Code | Name | Meaning |
|------|------|---------|
| 200 | SUCCESS | OK |
| 400 | BAD_REQUEST | commonApi bad params (HTTP 400 too) |
| 500 | SERVER_ERROR | Uncaught error, **or** production quote errors that still use 500 + the enum text |
| 501 / 502 | REQUEST_EXCEED_LIMIT / REQUEST_FOR_DAY_LIMIT | Rate limit → `InfowayRateLimitException` |
| 503 | KLINE_EXCEEDS_LIMIT | Too many bars |
| 505 | PRODUCTS_EXCEEDS_LIMIT | Too many symbols |
| 506 / 507 | PARAM_ERROR / PARAM_LOST | Bad / missing field |
| **508** | **PRODUCT_NOT_EXISTS** | **Unknown product** (not WS key expiry) |
| 509 | TOKEN_PERMISSION_EXPIRED | Token permission expired |
| 513 | TIME_LIMIT_ERROR | K-line `timestamp` older than the package |
| **514** | **NO_PERMISSION** | **No market entitlement** (not WS URL error) |

`/common/basic/*` only uses 200 / 400 / 500.

Verified against production (2026-09-21): quote-service business errors still arrive as **HTTP 200 + `ret=500`** with the English template (`All product not exists`, `Param error：klineType`, `Timestamp limit error…`, `Kline quantity exceeds the limit：500`). The SDK surfaces the **message**.

### WebSocket `code` (`WsErrorCode`)

| Code | Name | Meaning |
|------|------|---------|
| 501 / 502 | REQUEST_FREQUENCY_MIN_EXCEED / DAY | 60 frames/min → `InfowayRateLimitException` |
| 505 / 516 | PRODUCTS_QUANTITY_EXCEED | Per connection / all connections |
| 506 / 507 | PARAM_ERROR / PARAM_LOST | Bad or missing fields |
| **508–511** | **APIKEY_*** | **Expired / invalid / empty / blacklist** |
| 512 / 513 / 514 | Conn cap / heartbeat timeout / bad URL | 513 then close |
| 515 | PARAM_NOT_JSON | Inbound text was not JSON |
| 517–521 | Handshake | Missing key / no entitlement; 519/520 differ on Korea, Taiwan, news |

---

## 13. Full example

Pull a REST snapshot, then hang a live trade for 15 seconds.

```java
import com.google.gson.JsonObject;
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.InfowayWebSocket;
import io.infoway.sdk.KlineType;
import io.infoway.sdk.SymbolType;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.model.Trade;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class InfowayQuickstart {
    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("INFOWAY_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Set INFOWAY_API_KEY");
            System.exit(1);
        }

        try (InfowayClient client = InfowayClient.builder().apiKey(apiKey).build()) {
            List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
            System.out.println("REST last: " + trades.get(0).price());

            System.out.println("company: " + client.stockInfo().getCompany("AAPL.US", "zh-CN"));
            System.out.println("earnings: " + client.financial()
                    .getEarningStatus("AAPL.US", SymbolType.STOCK_US));
            System.out.println("day kline: " + client.crypto().getKline("BTCUSDT", KlineType.DAY, 5));
            System.out.println("quota: " + client.packages().getInfo());
        } catch (InfowayApiException e) {
            System.err.println(e.getMessage());
            return;
        }

        CountDownLatch firstTick = new CountDownLatch(1);
        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(apiKey)
                .business("crypto")
                .onTrade((JsonObject data) -> {
                    System.out.println("WS trade: " + data);
                    firstTick.countDown();
                })
                .onError(err -> System.err.println(err.getMessage()))
                .build();

        try {
            ws.connect();
            ws.subscribeTrade("BTCUSDT,ETHUSDT");
            if (!firstTick.await(45, TimeUnit.SECONDS)) {
                System.err.println("no trade push in 45s");
            }
        } finally {
            ws.close();
        }
    }
}
```

```bash
export INFOWAY_API_KEY=your-key
javac -cp infoway-sdk-0.3.0.jar InfowayQuickstart.java
java -cp infoway-sdk-0.3.0.jar:. InfowayQuickstart
```

Live contract / deep probe (opt-in):

```bash
cd sdks/java
INFOWAY_LIVE_TESTS=1 INFOWAY_API_KEY=your-key mvn test -Dtest=LiveContractTest
INFOWAY_LIVE_TESTS=1 INFOWAY_API_KEY=your-key mvn test -Dtest=LiveDeepTest
```

---

## Appendix: REST paths

Quotes (`{market}` = `stock` / `crypto` / `japan` / `india` / `korea` / `taiwan` / `common`):

- `GET /{market}/batch_trade/{codes}`
- `GET /{market}/batch_depth/{codes}`
- `POST /{market}/v2/batch_kline`

Basics / financials / quota:

- `GET /common/basic/symbols`
- `GET /common/basic/symbols/info`
- `GET /common/basic/symbols/adjustment_factors`
- `GET /common/basic/markets/trading_days`
- `GET /common/basic/markets/trading_schedule`
- `GET /common/basic/markets`
- `GET /common/basic/stock/detail`
- `GET /common/basic/financial/{earning_status|income_statement|revenue|cash_flow|balance_sheet|statistics|dividend|dividend_payout|earnings}`
- `GET /package/info`

Market / plate / stock:

- `GET /common/v2/basic/market/{temperature|indexes}`
- `GET /common/v2/basic/market/{breadth|turnover|leaders|overview|rank/categories}/{market}`
- `GET /common/v2/basic/market/rank/{market}/{key}`
- `GET /common/v2/basic/plate/{industry|concept|chart}/{market}`
- `GET /common/v2/basic/plate/{members|intro}/{plateSymbol}`
- `GET /common/v2/basic/stock/{valuation|ratings|company|panorama|concepts|events|drivers}/{symbol}`
