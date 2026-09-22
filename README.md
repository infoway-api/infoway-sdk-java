# Infoway Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.infoway/infoway-sdk.svg)](https://search.maven.org/artifact/io.infoway/infoway-sdk)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**English** | [中文](README_CN.md)

Official Infoway Java SDK for REST market data, fundamentals, and WebSocket streams.

| Item | Description |
| --- | --- |
| Artifact | `io.infoway:infoway-sdk:0.3.0` |
| Runtime | Java 21+ (OkHttp 4.x, Gson, SLF4J) |
| REST | `https://data.infoway.io` |
| Quotes WebSocket | `wss://data.infoway.io/ws` |
| News WebSocket | `wss://data.infoway.io/news` |
| Rate limits | [REST](https://docs.infoway.io/en-docs/getting-started/api-limitation/rest-api-limitation) · [WebSocket](https://docs.infoway.io/en-docs/getting-started/api-limitation/websocket-limitation) |
| Error codes | [REST](https://docs.infoway.io/en-docs/getting-started/error-codes/rest-api-error-codes) · [WebSocket](https://docs.infoway.io/en-docs/getting-started/error-codes/websocket-error-codes) |
| Endpoints | [Endpoints](https://docs.infoway.io/en-docs/getting-started/endpoints) |

If `apiKey` is omitted, the SDK reads `INFOWAY_API_KEY`. Close `InfowayClient` when finished.

## Contents

- [Install](#install)
- [Quick start](#quick-start)
- [Symbols](#symbols)
- [Client](#client)
- [REST](#rest)
- [Typed models](#typed-models)
- [WebSocket](#websocket)
- [Error codes](#error-codes)
- [Changelog](#changelog)
- [REST paths](#rest-paths)

## Install

Maven:

```xml
<dependency>
    <groupId>io.infoway</groupId>
    <artifactId>infoway-sdk</artifactId>
    <version>0.3.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'io.infoway:infoway-sdk:0.3.0'
```

## Quick start

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.KlineType;

try (InfowayClient client = InfowayClient.builder()
        .apiKey(System.getenv("INFOWAY_API_KEY"))
        .build()) {
    System.out.println(client.stock().getTrade("AAPL.US"));
    System.out.println(client.crypto().getKline("BTCUSDT", KlineType.DAY, 30));
    System.out.println(client.packages().getInfo());
}
```

## Symbols

| Market | Format | Valid | Invalid |
| --- | --- | --- | --- |
| US | `{code}.US` | `AAPL.US` | `AAPL` |
| Hong Kong | 5-digit + `.HK` | `00700.HK` | `700.HK` |
| Shanghai | `{code}.SH` | `600519.SH` | `600519.CN` |
| Shenzhen | `{code}.SZ` | `000001.SZ` | `000001.CN` |
| Japan | `{code}.JP` | `7203.JP` | |
| Korea | `{code}.KS` | `005930.KS` | |
| India | `{code}.IN` | `RELIANCE.IN` | |
| Taiwan | `{code}.TW` | `2330.TW` | |
| Crypto | Pair | `BTCUSDT` | |
| FX | Pair | `USDJPY` | |

`basic()` / `financial()` take a product `type` such as `STOCK_US` or `CRYPTO`, not a market code like `US`.

## Client

| Method | Default | Description |
| --- | --- | --- |
| `apiKey(key)` | `INFOWAY_API_KEY` | API key |
| `baseUrl(url)` | `https://data.infoway.io` | REST base URL |
| `timeout(secs)` | `15` | Per-request timeout (seconds) |
| `maxRetries(n)` | `3` | Retry count |

| Client | Use |
| --- | --- |
| `stock()` / `crypto()` / `japan()` / `india()` / `korea()` / `taiwan()` / `common()` | Trade, depth, candles |
| `basic()` / `packages()` | Symbols, calendar, plan |
| `market()` / `plate()` | Overview, sectors |
| `stockInfo()` / `financial()` | Fundamentals, statements |

Prefer enums: `KlineType`, `SymbolType`, `Market`, `Lang`, `NewsLang`, `PeriodType`, `WsBusiness`, `RankSort`, `SortOrder`, `ScheduleType`. String overloads remain.

## REST

Join symbols with commas. Rate limits: [REST API Limitation](https://docs.infoway.io/en-docs/getting-started/api-limitation/rest-api-limitation).

### Quotes

Same methods on `stock` / `crypto` / `japan` / `india` / `korea` / `taiwan` / `common`.

| Method | Description |
| --- | --- |
| `getTrade(codes)` | Latest trade |
| `getDepth(codes)` | Order book |
| `getKline(codes, type, count)` | Candles |
| `getKline(codes, type, count, timestamp)` | Candles ending at a unix-seconds timestamp (minute / hour) |

Trade fields: `s` symbol, `p` price, `v` volume, `vw` turnover, `t` milliseconds, `td` side (`0` / `1` buy / `2` sell). Candles are under `respList`; `t` is seconds. Up to 500 bars per symbol; multi-symbol calls return 2 bars each.

```java
client.stock().getTrade("AAPL.US,TSLA.US");
client.crypto().getDepth("BTCUSDT");
client.korea().getTrade("005930.KS");
client.taiwan().getTrade("2330.TW");
client.crypto().getKline("BTCUSDT", KlineType.MIN_1, 100);
```

| Enum | Value | Interval |
| --- | --- | --- |
| `MIN_1` / `MIN_5` / `MIN_15` / `MIN_30` | 1–4 | Minutes |
| `HOUR_1` / `HOUR_2` / `HOUR_4` | 5–7 | Hours |
| `DAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` | 8–12 | Daily and above |

### Basic info

Dates use `YYYYMMDD`. `getTradingHours` is deprecated; use `getTradingSchedule`. `getTradingScheduleByType` accepts `ENERGY` / `FOREX` / `FUTURES` / `METAL` / `INDICES`.

```java
client.basic().getSymbols(SymbolType.STOCK_US);
client.basic().getSymbolInfo(SymbolType.STOCK_US, "AAPL.US");
client.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US");
client.basic().getAdjustmentFactors("AAPL.US", Market.US, "20260801", "20260815");
client.basic().getTradingDays(Market.US, "20260801", "20260815");
client.basic().getTradingSchedule();
client.basic().getTradingScheduleByType(ScheduleType.ENERGY);
client.basic().getMarkets();
client.packages().getInfo();
```

`packages().getInfo()` returns `packageName`, `expireTime`, `apiNumPerSec`, `maxWsConNum`, `maxNum`, `maxYearHisData`, `allWsNum`.

### Overview / sectors / stock info / financials

```java
client.market().getTemperature(Lang.ZH_CN, Market.HK, Market.US);
client.market().getBreadth(Market.US, Lang.ZH_CN);
client.market().getTurnover(Market.US);
client.market().getIndexes(Lang.EN);
client.market().getLeaders(Market.US, 10);
client.market().getOverview(Market.US, Lang.ZH_CN);
client.market().getRankCategories(Market.US);
client.market().getRank(Market.US, "all", RankSort.CHG, SortOrder.DESC, 30, 0, Lang.EN);

client.plate().getIndustry(Market.HK, 200);
client.plate().getConcept("HK", 100);
client.plate().getMembers("IN20293.HK", 0, 50);
client.plate().getIntro("IN20293.HK");
client.plate().getChart("HK", 50);

client.stockInfo().getValuation("AAPL.US");
client.stockInfo().getRatings("AAPL.US");
client.stockInfo().getCompany("AAPL.US", Lang.ZH_CN);
client.stockInfo().getPanorama("AAPL.US");
client.stockInfo().getConcepts("AAPL.US");
client.stockInfo().getEvents("AAPL.US", 20);
client.stockInfo().getDrivers("AAPL.US");

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

Rank `key` values come from `getRankCategories`. Financial methods require `symbol` and `type`. `period_type`: `fq` quarter, `fy` year, `fh` half-year. Optional stock-info `lang`: `en` / `zh-CN`.

## Typed models

Default return type is `JsonElement`. Use `getTradeParsed` / `getDepthParsed` / `getKlineParsed` for normalized models.

```java
List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
List<Kline> bars = client.crypto().getKlineParsed("BTCUSDT", KlineType.MIN_1, 20);
```

| Raw | Normalized |
| --- | --- |
| Price / volume as strings | `BigDecimal` |
| Trade / depth `t` in ms; candle `t` in seconds | `Instant` |
| REST `pc` / WS `pfr` (`"0.03%"`) | `changePercent = 0.0003` |
| Candle `respList` | `List<Kline>` |
| Book `a`/`b` as `[[price…],[qty…]]` | `List<DepthLevel>` |
| `vw` | `turnover` |

## WebSocket

### Quotes

`business` must match the symbol market. Callbacks receive `data`. 60 frames per minute per connection (subscribe, unsubscribe, heartbeat). See [WebSocket Limitation](https://docs.infoway.io/en-docs/getting-started/api-limitation/websocket-limitation). Subscriptions issued before `connect()` are sent after open.

```java
InfowayWebSocket ws = InfowayWebSocket.builder()
        .apiKey(System.getenv("INFOWAY_API_KEY"))
        .business(WsBusiness.CRYPTO)
        .printFrames(false)
        .onTrade(data -> System.out.println(data.get("s") + " " + data.get("p")))
        .onDepth(data -> System.out.println(data.get("s")))
        .onKline(data -> System.out.println(data.get("s")))
        .onError(err -> System.err.println(err.getMessage()))
        .build();

ws.connect();
ws.subscribeTrade("BTCUSDT,ETHUSDT");
ws.subscribeDepth("BTCUSDT");
ws.subscribeKline("BTCUSDT", KlineType.MIN_1);
ws.unsubscribeKline("BTCUSDT", KlineType.MIN_1);
ws.close();
```

Equity trade types: `subscribeTrade(codes, true)` adds `ty`.

| Behavior | Description |
| --- | --- |
| Heartbeat | `10010` every 30 seconds; server does not reply |
| Reconnect | Replays the current subscription set |
| `onReconnect` | After a later successful open |
| `onDisconnect` | Unexpected drop only. `close()` does not fire it |
| HTTP 401 | Stops reconnecting |

| Dir | Code | Description |
| --- | --- | --- |
| out | 10000 / 10003 / 10006 | Subscribe trade / depth / kline |
| out | 11000 / 11001 / 11002 | Unsubscribe |
| out | 10010 | Heartbeat (`ack=1` yields 10011) |
| in | 10001 / 10004 / 10007 | Subscribe ack |
| in | 10002 / 10005 / 10008 | Push |
| in | 11010 | Unsubscribe ack |
| in | 200 | Connected |

An ack means the request was accepted. A wrong `business`, unknown symbol, or closed market still acks and then stays silent. Merge symbols into one comma-separated string.

### News

`wss://data.infoway.io/news` requires a separate entitlement. One news connection per key.

```java
InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
        .apiKey(System.getenv("INFOWAY_API_KEY"))
        .lang(NewsLang.ZH_HANS)
        .onNews(item -> System.out.println(item.get("title")))
        .onNewsParsed(item -> System.out.println(item.title()))
        .build();
news.connect();
news.unsubscribe();
news.subscribe(NewsLang.EN);
news.close();
```

| Dir | Code | Description |
| --- | --- | --- |
| out | 10020 / 11020 | Subscribe / unsubscribe |
| in | 10021 / 10022 | Ack / push |

Fields: `dk`, `country`, `lang`, `route`, `title`, `published` (seconds), `urgency`, `provider`, `symbols[]`, `link`, `content`, `sd`. A later subscribe replaces the language.

## Error codes

REST uses `ret`. WebSocket uses `code`. `508`–`514` mean different things on each side. Full tables: [REST API Error Codes](https://docs.infoway.io/en-docs/getting-started/error-codes/rest-api-error-codes) and [WebSocket Error Codes](https://docs.infoway.io/en-docs/getting-started/error-codes/websocket-error-codes).

```java
try {
    client.stock().getTrade("INVALID");
} catch (InfowayAuthException e) {
    System.err.println(e.getMsg());
} catch (InfowayRateLimitException e) {
    System.err.println(e.getRet() + " " + e.getMsg());
} catch (InfowayApiException e) {
    System.err.println(e.getMessage());
    System.err.println(e.getTraceId());
}
```

REST budget is about 1200 calls/minute/key. HTTP 429, `ret` 501/502, or `{"detail":"Rate limit exceeded"}` are retried. An invalid key raises `InfowayAuthException` on REST; WebSocket handshake HTTP 401 does not reconnect.

| REST `ret` | Description |
| --- | --- |
| 200 | Success |
| 400 | Bad request |
| 500 | Server error |
| 501 / 502 | Rate limit |
| 503 | Candle count exceeded |
| 505 | Symbol count exceeded |
| 506 / 507 | Invalid / missing parameter |
| 508 | Symbol not found |
| 509 | Permission expired |
| 513 | Timestamp outside plan history |
| 514 | No permission |

| WebSocket `code` | Description |
| --- | --- |
| 501 / 502 | Rate limit |
| 505 / 516 | Subscription count exceeded |
| 506 / 507 | Invalid / missing parameter |
| 508–511 | API key expired / invalid / empty / blacklisted |
| 512 | Connection count exceeded |
| 513 | Heartbeat timeout |
| 515 | Not JSON |
| 517–521 | Handshake failed |

## Changelog

**0.3.0** — `packages().getInfo()`, `getTradingScheduleByType`, `InfowayIoException`, Korea / Taiwan / financial clients, `RestErrorCode` / `WsErrorCode`, enum parameters, `printFrames` off by default, news `onNewsParsed`.

**0.2.0** — `basic()` parameters fixed (breaking):

| Old | New |
| --- | --- |
| `getSymbols("US")` | `getSymbols(SymbolType.STOCK_US)` |
| `getSymbolInfo("AAPL.US")` | `getSymbolInfo(SymbolType.STOCK_US, "AAPL.US")` |
| `getAdjustmentFactors("AAPL.US")` | `getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815")` |
| `getTradingDays("US")` | `getTradingDays("US", "20260801", "20260815")` |
| `getTradingHours("US")` | `getTradingSchedule()` |

WebSocket callbacks now receive `data`. `unsubscribeKline` is per interval. An invalid key stops reconnecting.

## REST paths

`{market}` = `stock` / `crypto` / `japan` / `india` / `korea` / `taiwan` / `common`. Full list: [Endpoints](https://docs.infoway.io/en-docs/getting-started/endpoints).

| API | Path |
| --- | --- |
| Latest trade | `GET /{market}/batch_trade/{codes}` |
| Order book | `GET /{market}/batch_depth/{codes}` |
| Candles | `POST /{market}/v2/batch_kline` |
| Symbols / calendar / financials | `GET /common/basic/*` |
| Overview / sectors / stock info | `GET /common/v2/basic/*` |
| Package | `GET /package/info` |

## License

MIT. API key: [infoway.io](https://infoway.io).
