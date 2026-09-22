# Infoway Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.infoway/infoway-sdk.svg)](https://search.maven.org/artifact/io.infoway/infoway-sdk)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

[English](README.md) | **中文**

Infoway 官方 Java SDK。覆盖 REST 行情、基础信息、市场概览、板块、个股、财务，以及行情 / 新闻 WebSocket。

| 项目 | 说明 |
| --- | --- |
| 坐标 | `io.infoway:infoway-sdk:0.4.0` |
| 运行环境 | Java 21+（OkHttp 4.x、Gson、SLF4J） |
| REST | `https://data.infoway.io` |
| 行情 WebSocket | `wss://data.infoway.io/ws` |
| 新闻 WebSocket | `wss://data.infoway.io/news` |
| 接口频率 | [HTTP](https://docs.infoway.io/getting-started/api-limitation/http) · [WebSocket](https://docs.infoway.io/getting-started/api-limitation/websocket) |
| 错误码 | [HTTP](https://docs.infoway.io/getting-started/error-codes/http) · [WebSocket](https://docs.infoway.io/getting-started/error-codes/websocket) |
| 地址 | [行情地址](https://docs.infoway.io/getting-started/api-endpoints) |

未传入 `apiKey` 时读取环境变量 `INFOWAY_API_KEY`。`InfowayClient` 实现 `Closeable`，用完请关闭。

## 目录

- [安装](#安装)
- [快速开始](#快速开始)
- [标的代码](#标的代码)
- [客户端](#客户端)
- [REST](#rest)
- [类型化模型](#类型化模型)
- [WebSocket](#websocket)
- [错误码](#错误码)
- [版本变更](#版本变更)
- [REST 路径](#rest-路径)

## 安装

Maven：

```xml
<dependency>
    <groupId>io.infoway</groupId>
    <artifactId>infoway-sdk</artifactId>
    <version>0.4.0</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.infoway:infoway-sdk:0.4.0'
```

## 快速开始

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

## 标的代码

| 市场 | 格式 | 正确 | 错误 |
| --- | --- | --- | --- |
| 美股 | `{代码}.US` | `AAPL.US` | `AAPL` |
| 港股 | 5 位 + `.HK` | `00700.HK` | `700.HK` |
| A 股上海 | `{代码}.SH` | `600519.SH` | `600519.CN` |
| A 股深圳 | `{代码}.SZ` | `000001.SZ` | `000001.CN` |
| 日股 | `{代码}.JP` | `7203.JP` | |
| 韩股 | `{代码}.KS` | `005930.KS` | |
| 印股 | `{代码}.IN` | `RELIANCE.IN` | |
| 台股 | `{代码}.TW` | `2330.TW` | |
| 加密货币 | 交易对 | `BTCUSDT` | |
| 外汇 | 货币对 | `USDJPY` | |

`basic()` / `financial()` 的 `type` 用品种类型（`STOCK_US`、`STOCK_CN`、`CRYPTO` 等），不要传市场码 `US`。

## 客户端

| 方法 | 默认 | 说明 |
| --- | --- | --- |
| `apiKey(key)` | `INFOWAY_API_KEY` | API Key |
| `baseUrl(url)` | `https://data.infoway.io` | REST 根地址 |
| `timeout(secs)` | `15` | 单次超时（秒） |
| `maxRetries(n)` | `3` | 失败重试 |

| 入口 | 用途 |
| --- | --- |
| `stock()` / `crypto()` / `japan()` / `india()` / `korea()` / `taiwan()` / `common()` | 成交、盘口、K 线 |
| `basic()` / `packages()` | 标的、日历、套餐 |
| `market()` / `plate()` | 市场概览、板块 |
| `stockInfo()` / `financial()` | 个股资料、财务 |

推荐枚举：`KlineType`、`SymbolType`、`Market`、`Lang`、`NewsLang`、`PeriodType`、`WsBusiness`、`RankSort`、`SortOrder`、`ScheduleType`。字符串重载仍可用。

## REST

多个标的用英文逗号分隔。频率见 [HTTP接口限制](https://docs.infoway.io/getting-started/api-limitation/http)。

### 行情

`stock` / `crypto` / `japan` / `india` / `korea` / `taiwan` / `common` 方法相同。

| 方法 | 说明 |
| --- | --- |
| `getTrade(codes)` | 最新成交 |
| `getDepth(codes)` | 盘口 |
| `getKline(codes, type, count)` | K 线 |
| `getKline(codes, type, count, timestamp)` | K 线，截止到指定秒（分钟 / 小时） |

成交字段：`s` 代码、`p` 价格、`v` 量、`vw` 成交额、`t` 毫秒、`td` 方向（0 / 1 买 / 2 卖）。K 线在 `respList` 中，`t` 为秒。单标的最多 500 根；多标的时每个返回最近 2 根。

```java
client.stock().getTrade("AAPL.US,TSLA.US");
client.crypto().getDepth("BTCUSDT");
client.korea().getTrade("005930.KS");
client.taiwan().getTrade("2330.TW");
client.crypto().getKline("BTCUSDT", KlineType.MIN_1, 100);
```

| 枚举 | 值 | 周期 |
| --- | --- | --- |
| `MIN_1` / `MIN_5` / `MIN_15` / `MIN_30` | 1–4 | 分钟 |
| `HOUR_1` / `HOUR_2` / `HOUR_4` | 5–7 | 小时 |
| `DAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` | 8–12 | 日及以上 |

### 基础信息

日期 `YYYYMMDD`。`getTradingHours` 已弃用，请用 `getTradingSchedule`。`getTradingScheduleByType` 仅接受 `ENERGY` / `FOREX` / `FUTURES` / `METAL` / `INDICES`。

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

`packages().getInfo()` 返回 `packageName`、`expireTime`、`apiNumPerSec`、`maxWsConNum`、`maxNum`、`maxYearHisData`、`allWsNum`。

### 市场 / 板块 / 个股 / 财务

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

排行 `key` 来自 `getRankCategories`。财务每个方法需要 `symbol` + `type`。`period_type`：`fq` 季报、`fy` 年报、`fh` 中报。个股 `lang` 可选 `en` / `zh-CN`。

## 类型化模型

默认返回 `JsonElement`。需要归一化时用 `getTradeParsed` / `getDepthParsed` / `getKlineParsed`。

```java
List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
List<Kline> bars = client.crypto().getKlineParsed("BTCUSDT", KlineType.MIN_1, 20);
```

| 原样 | 归一化 |
| --- | --- |
| 价格 / 量为字符串 | `BigDecimal` |
| 成交 / 盘口 `t` 毫秒；K 线 `t` 秒 | `Instant` |
| REST `pc` / WS `pfr`（`"0.03%"`） | `changePercent = 0.0003` |
| K 线 `respList` | `List<Kline>` |
| 盘口 `a`/`b` 为 `[[价格…],[数量…]]` | `List<DepthLevel>` |
| `vw` | `turnover`（成交额） |

## WebSocket

### 行情

`business` 必须与标的市场一致。回调收到的是 `data`。单连接每分钟最多 60 帧（订阅、退订、心跳合计），见 [WebSocket限制](https://docs.infoway.io/getting-started/api-limitation/websocket)。可在 `connect()` 前订阅。

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

股票成交类型：`subscribeTrade(codes, true)`，推送字段 `ty`。

| 行为 | 说明 |
| --- | --- |
| 心跳 | 每 30 秒发送 `10010`，服务端不回包 |
| 重连 | 断线后自动重连并补发当前订阅 |
| `onReconnect` | 再次连上时触发 |
| `onDisconnect` | 仅意外掉线。`close()` 不触发、不重连 |
| HTTP 401 | 停止重连 |

| 方向 | 协议号 | 说明 |
| --- | --- | --- |
| 出 | 10000 / 10003 / 10006 | 订阅成交 / 盘口 / K 线 |
| 出 | 11000 / 11001 / 11002 | 退订 |
| 出 | 10010 | 心跳（`ack=1` 时回 10011） |
| 入 | 10001 / 10004 / 10007 | 订阅确认 |
| 入 | 10002 / 10005 / 10008 | 推送 |
| 入 | 11010 | 退订确认 |
| 入 | 200 | 连接成功 |

ack 只表示请求被接受。`business` 订错、代码不存在或休市时仍会 ack，但不会推数据。多个代码必须合并成一个逗号串。

### 新闻

地址 `wss://data.infoway.io/news`，需单独授权。每个 Key 仅允许一条新闻连接。

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

| 方向 | 协议号 | 说明 |
| --- | --- | --- |
| 出 | 10020 / 11020 | 订阅 / 退订 |
| 入 | 10021 / 10022 | 确认 / 推送 |

推送字段：`dk`、`country`、`lang`、`route`、`title`、`published`（秒）、`urgency`、`provider`、`symbols[]`、`link`、`content`、`sd`。再次订阅覆盖语言。

## 错误码

REST 看 `ret`，WebSocket 看 `code`。`508`–`514` 两套含义不同。完整列表见 [HTTP错误码](https://docs.infoway.io/getting-started/error-codes/http) 与 [WebSocket错误码](https://docs.infoway.io/getting-started/error-codes/websocket)。

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

REST 限额约 1200 次/分钟/Key。HTTP 429、`ret` 501/502 或 `{"detail":"Rate limit exceeded"}` 时会重试。无效 Key：REST 抛 `InfowayAuthException`；WebSocket 握手 HTTP 401 且不重连。

| REST `ret` | 说明 |
| --- | --- |
| 200 | 成功 |
| 400 | 参数错误 |
| 500 | 服务端错误 |
| 501 / 502 | 频率超限 |
| 503 | K 线数量超限 |
| 505 | 标的数量超限 |
| 506 / 507 | 参数错误 / 缺失 |
| 508 | 标的不存在 |
| 509 | 权限过期 |
| 513 | 历史时间超出套餐 |
| 514 | 无权限 |

| WebSocket `code` | 说明 |
| --- | --- |
| 501 / 502 | 频率超限 |
| 505 / 516 | 订阅数量超限 |
| 506 / 507 | 参数错误 / 缺失 |
| 508–511 | API Key 过期 / 无效 / 为空 / 黑名单 |
| 512 | 连接数超限 |
| 513 | 心跳超时 |
| 515 | 非 JSON |
| 517–521 | 握手失败 |

## 版本变更

**0.4.0** — **破坏性变更。** 参数上限改为请求前在客户端校验：超过 100 个标的抛 `ret` 505，超过 500 根抛 503，多标的且超过 2 根抛 506（此前服务端会静默截断成 2 根并返回 200）。显式传入的空 API Key 不再回落到 `INFOWAY_API_KEY`，且 Key 会被去除首尾空白。`ret=500` 的信封会被重新分类为 501–514。WebSocket 收到终止码后直接停止，不再重连。对已关闭的客户端发起调用会抛错。

**0.3.0** — `packages().getInfo()`、`getTradingScheduleByType`、`InfowayIoException`、韩股 / 台股 / 财务客户端、`RestErrorCode` / `WsErrorCode`、枚举参数、`printFrames` 默认关闭、新闻 `onNewsParsed`。

**0.2.0** — `basic()` 参数修正（破坏性）：

| 旧写法 | 新写法 |
| --- | --- |
| `getSymbols("US")` | `getSymbols(SymbolType.STOCK_US)` |
| `getSymbolInfo("AAPL.US")` | `getSymbolInfo(SymbolType.STOCK_US, "AAPL.US")` |
| `getAdjustmentFactors("AAPL.US")` | `getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815")` |
| `getTradingDays("US")` | `getTradingDays("US", "20260801", "20260815")` |
| `getTradingHours("US")` | `getTradingSchedule()` |

WebSocket 回调改为 `data` 本体；`unsubscribeKline` 按周期退订；无效 Key 停止重连。

## REST 路径

`{market}` = `stock` / `crypto` / `japan` / `india` / `korea` / `taiwan` / `common`。完整列表见 [行情地址](https://docs.infoway.io/getting-started/api-endpoints)。

| 接口 | 路径 |
| --- | --- |
| 最新成交 | `GET /{market}/batch_trade/{codes}` |
| 盘口 | `GET /{market}/batch_depth/{codes}` |
| K 线 | `POST /{market}/v2/batch_kline` |
| 品种 / 日历 / 财务 | `GET /common/basic/*` |
| 市场 / 板块 / 个股 | `GET /common/v2/basic/*` |
| 套餐 | `GET /package/info` |

## 许可证

MIT。API Key：[infoway.io](https://infoway.io)。
