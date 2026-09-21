# Infoway Java SDK 使用说明

面向量化、行情接入和基本面查询的 Java 21+ 使用指南。安装说明与版本变更见 [README_CN.md](README_CN.md)，官方接口定义见 [docs.infoway.io](https://docs.infoway.io)。英文对照：[USAGE.md](USAGE.md)。

- 包坐标：`io.infoway:infoway-sdk:0.3.0`
- REST 默认地址：`https://data.infoway.io`
- 行情 WebSocket：`wss://data.infoway.io/ws`
- 新闻 WebSocket：`wss://data.infoway.io/news`

---

## 目录

1. [安装与客户端](#1-安装与客户端)
2. [标的代码约定](#2-标的代码约定)
3. [REST：多市场行情](#3-rest多市场行情)
4. [REST：基础信息](#4-rest基础信息)
5. [REST：市场概览](#5-rest市场概览)
6. [REST：板块](#6-rest板块)
7. [REST：个股资料](#7-rest个股资料)
8. [REST：财务](#8-rest财务)
9. [类型化模型](#9-类型化模型)
10. [WebSocket：实时行情](#10-websocket实时行情)
11. [WebSocket：新闻](#11-websocket新闻)
12. [错误处理与限额](#12-错误处理与限额)
13. [完整示例](#13-完整示例)

---

## 1. 安装与客户端

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

未显式传入 `apiKey` 时，SDK 读取环境变量 `INFOWAY_API_KEY`。`InfowayClient` 实现了 `Closeable`，用完请关闭。

```java
import io.infoway.sdk.InfowayClient;

public class CreateClient {
    public static void main(String[] args) {
        try (InfowayClient client = InfowayClient.builder()
                .apiKey(System.getenv("INFOWAY_API_KEY"))
                .baseUrl("https://data.infoway.io") // 可省略
                .timeout(15)                        // 秒，默认 15
                .maxRetries(3)                      // 默认 3，指数退避
                .build()) {
            System.out.println(client.crypto().getTrade("BTCUSDT"));
        }
    }
}
```

| Builder | 默认值 | 说明 |
|---------|--------|------|
| `apiKey(key)` | `INFOWAY_API_KEY` | API Key |
| `baseUrl(url)` | `https://data.infoway.io` | REST 根地址 |
| `timeout(secs)` | `15` | 单次请求超时（秒） |
| `maxRetries(n)` | `3` | 失败重试次数 |

入口按数据域拆分：

| 入口 | 用途 |
|------|------|
| `stock()` / `crypto()` / `japan()` / `india()` / `korea()` / `taiwan()` / `common()` | 成交、盘口、K 线 |
| `basic()` | 标的、日历、个股档案 |
| `packages()` | 当前 Key 的套餐额度 |
| `market()` | 情绪、涨跌家数、换手、排行 |
| `plate()` | 行业 / 概念板块 |
| `stockInfo()` | 估值、评级、公司资料 |
| `financial()` | 财报、分红、盈利 |

原有的 `String` 参数都还在，下面这些推荐改用枚举，避免写错线上取值：

| 枚举 | 线上取值 | 用在 |
|------|----------|------|
| `KlineType` | `MIN_1`…`YEAR`（1–12） | K 线 REST / WS |
| `SymbolType` | `STOCK_US` / `STOCK_CN` / `CRYPTO`… | `basic()` / `financial()` / 个股档案 |
| `Market` | `HK` `US` `CN` `JP` `KS` `TW` `IN` | 市场概览、板块、日历；多市场用 `Market.join` 或 varargs |
| `Lang` | `EN`=`en`，`ZH_CN`=`zh-CN` | REST 的 `lang` |
| `NewsLang` | `EN` `ZH_HANS` `ZH_HANT` `JA` `KO`… | 新闻 WS |
| `PeriodType` | `FQ` / `FY` / `FH` | 财务报表 |
| `WsBusiness` | `STOCK` `CRYPTO` `KOREA` `TAIWAN`… | 行情 WS 的 `business` |
| `RankSort` | `CHG` `LAST_DONE` `VOLUME`… | 排行 `sort` |
| `SortOrder` | `ASC` `DESC` | 排行 `order` |
| `ScheduleType` | `ENERGY` `FOREX` `FUTURES` `METAL` `INDICES` | 交易时间表过滤 |
| `WsCode` / `WsErrorCode` / `RestErrorCode` | 协议号 / WS 5xx / REST `ret` | 收发与错误 |

---

## 2. 标的代码约定

| 市场 | 后缀 / 形式 | 正确示例 | 错误示例 |
|------|-------------|----------|----------|
| 美股 | `.US` | `AAPL.US` | `AAPL` |
| 港股 | `.HK`，代码补足 5 位 | `00700.HK` | `700.HK` |
| A 股上海 | `.SH` | `600519.SH` | `600519.CN` |
| A 股深圳 | `.SZ` | `000001.SZ` | `000001.CN` |
| 日股 | `.JP` | `7203.JP` | |
| 韩股 | `.KS` | `005930.KS` | |
| 印股 | `.IN` | | |
| 台股 | `.TW` | `2330.TW` | |
| 加密货币 | 交易对 | `BTCUSDT` | |
| 外汇等 | 货币对 | `USDJPY` | |

`basic()` / `financial()` / `getStockDetail()` 的 `type` 必须用产品类型，不能传市场码 `US`：

`STOCK_US` `STOCK_CN` `STOCK_HK` `STOCK_JP` `STOCK_KS` `STOCK_IN` `STOCK_TW`  
`CRYPTO` `FOREX` `FUTURES` `ENERGY` `METAL` `INDICES`

传 `market=US` 给 `getSymbols` 会得到 HTTP 400：`Required parameter 'type' is not present.`

---

## 3. REST：多市场行情

七个市场客户端方法相同，只是路径前缀不同。多个代码用英文逗号拼接。

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

成交字段：`s` 代码、`p` 价格、`v` 量、`vw` 成交额、`t` 毫秒时间戳、`td` 方向（0 默认 / 1 买 / 2 卖）。  
K 线外层是按品种包一层，K 线列表在 `respList`；`t` 是**秒**级字符串。`timestamp` 只对分钟 / 小时 K 有意义。

| 枚举 | 值 | 周期 |
|------|----|------|
| `MIN_1` / `MIN_5` / `MIN_15` / `MIN_30` | 1–4 | 分钟 |
| `HOUR_1` / `HOUR_2` / `HOUR_4` | 5–7 | 小时 |
| `DAY` / `WEEK` / `MONTH` / `QUARTER` / `YEAR` | 8–12 | 日及以上 |

K 线单品种最多 **500** 根；多品种请求会被服务端截成每个品种 2 根。

---

## 4. REST：基础信息

日期一律 `YYYYMMDD`。

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

`getTradingHours` 已弃用，请用 `getTradingSchedule`。交易时间表没有市场过滤；`getTradingSchedule("US")` 与无参相同。按品种过滤用 `ScheduleType`（`ENERGY` / `FOREX` / `FUTURES` / `METAL` / `INDICES`）。传入 `SymbolType.STOCK_US` 会在发请求前抛 `IllegalArgumentException`。

`packages().getInfo()` 返回当前 Key 的套餐：`packageName`、`expireTime`、`apiNumPerSec`、`maxWsConNum`、`maxNum`、`maxYearHisData`、`allWsNum`。

---

## 5. REST：市场概览

`market` 用 `Market`，`lang` 用 `Lang`。温度接口可一次传多个市场。

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

排行榜的 `key` 来自 `getRankCategories`。`getRankConfig` 已弃用（服务端 404）。

---

## 6. REST：板块

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

## 7. REST：个股资料

均支持可选 `lang`：`en` 或 `zh-CN`。

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

## 8. REST：财务

每个方法都要 `symbol` + `type`。报表类可带 `period_type`：

| 值 | 含义 |
|----|------|
| `fq` | 季报 |
| `fy` | 年报 |
| `fh` | 中报 |

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

港股示例：`client.financial().getDividend("00700.HK", SymbolType.STOCK_HK)`。

---

## 9. 类型化模型

默认返回 Gson `JsonElement`。需要 SDK 消化字段差异时用 `*Parsed` 或 `Normalizer`。

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

| 线上原样 | 归一化后 |
|----------|----------|
| 价格 / 量是字符串 `"305.771"` | `BigDecimal` |
| 成交 / 盘口 `t` 为毫秒数字；K 线 `t` 为秒字符串 | `Instant` |
| REST 涨跌幅 `pc`、WS 涨跌幅 `pfr`，值为 `"0.03%"` | `changePercent = 0.0003` |
| K 线多套 `respList` | 展平为 `List<Kline>` |
| 盘口 `a`/`b` 是 `[[价格…],[数量…]]` | `List<DepthLevel>` |
| `vw` 实际是成交额 | `turnover` |

---

## 10. WebSocket：实时行情

独立于 REST 客户端。`business` 必须和市场一致，订错频道会 ack 成功但永远不推数据。

`printFrames` 默认 **false**：入站帧只写 DEBUG。调试时用 `.printFrames(true)`（INFO + stdout），或用 `onFrame` 自己处理。REST 和 WebSocket 都可以不传 `apiKey`，此时读环境变量 `INFOWAY_API_KEY`。

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
                .printFrames(true)           // 可选：打印服务端全部回包，默认 false
                .onFrame(System.out::println) // 再抄一份给自己的日志也可以
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

股票成交类型（碎股、竞价等）需要 `includeTy=true`。加密货币即使打开也不会出 `ty` 字段。

```java
InfowayWebSocket stock = InfowayWebSocket.builder()
        .apiKey(System.getenv("INFOWAY_API_KEY"))
        .business(WsBusiness.STOCK)
        .onTrade(data -> System.out.println(data.get("s") + " ty=" + data.get("ty")))
        .build();
stock.connect();
stock.subscribeTrade("AAPL.US,TSLA.US", true);
```

回调拿到的是 **`data` 本体**，不是 `{"code":10002,"data":{...}}`。可在 `connect()` 之前订阅，连上后会补发。

生命周期（重连 / 订阅 / 关闭）：

- 心跳 `10010` 每 30 秒一次，**每个存活会话只启一代定时器**。一次掉线最多安排 **一次** 重连（1 秒起、30 秒封顶）。
- `onReconnect` 只在**再次**连上时触发，首次 `connect()` 不会调用。
- `onDisconnect` 只表示意外掉线。`close()` **不**触发它、也**不**重连，并会打断退避睡眠，关闭马上返回。
- 客户端保存的是**目标订阅集**。已退订的内容重连后不会再发；重连窗口里的新订阅会在下一跳补发。K 线按周期退订，其它周期保留。
- HTTP 401 仍然停止重连。

| 现象 | 原因 |
|------|------|
| 首帧纯文本 `You have permission...` | `business=stock` 的欢迎文本，SDK 会跳过 |
| `{"code":200,"msg":"ws connect success"}` | 欢迎帧，不是错误 |
| ack `ok` 之后一直没数据 | business 订错、代码不存在或休市；ack 只表示请求被接受 |
| 心跳没有回包 | 服务端对 `10010` 不响应，不要用“等心跳超时”判断断线 |
| 发了很多帧后被踢 | 单连接 **60 帧/分钟**（订阅 + 退订 + 心跳合计），多个代码必须合并成一个逗号串 |
| HTTP 401 | Key 无效或没有该频道权限；抛 `InfowayAuthException` 并停止重连 |

协议号：

| 方向 | 码 | 含义 |
|------|----|------|
| 出 | 10000 / 10003 / 10006 | 订阅成交 / 盘口 / K 线 |
| 出 | 11000 / 11001 / 11002 | 退订 |
| 出 | 10010 | 心跳（30 秒一次；带 `ack=1` 才会收到 10011） |
| 入 | 10001 / 10004 / 10007 | 订阅确认 |
| 入 | 10002 / 10005 / 10008 | 实时推送 |
| 入 | 10011 | 心跳确认（可选） |
| 入 | 11010 | 退订确认（成交/盘口/K线/新闻共用） |
| 入 | 200 | 欢迎帧 |
| 入 | 500–521 | 服务端错误，回调 `onError`（501/502 为限流；见 `WsErrorCode`） |

---

## 11. WebSocket：新闻

新闻走独立地址 `wss://data.infoway.io/news`，需要单独授权。一个 Key 只允许一条新闻连接。

```java
import io.infoway.sdk.InfowayNewsWebSocket;
import io.infoway.sdk.NewsLang;

public class NewsSocketExample {
    public static void main(String[] args) throws InterruptedException {
        InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
                .apiKey(System.getenv("INFOWAY_API_KEY"))
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

        news.unsubscribe(); // 协议号 11020
        news.subscribe(NewsLang.EN); // 再次订阅会覆盖语言
        news.close();
    }
}
```

推送字段：`dk`（去重）、`country`、`lang`、`route`、`title`、`published`（**秒**）、`urgency`（越小越急）、`provider`、`symbols[]`、`link`、`content`、`sd`（摘要）。

| 方向 | 码 | 含义 |
|------|----|------|
| 出 | 10020 / 11020 | 订阅 / 退订 |
| 入 | 10021 / 10022 | 确认 / 推送 |

没有新闻权限的 Key 会在握手阶段收到 HTTP 401。当前 `lang` 会在重连时回放；`unsubscribe()` 清空语言后重连不再订阅。`onReconnect` / `onDisconnect` 规则与行情通道相同。

---

## 12. 错误处理与限额

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
            System.err.println("认证失败: " + e.getMsg());
        } catch (InfowayRateLimitException e) {
            System.err.printf("限流 [%d %s] %s%n", e.getRet(), e.getErrorName(), e.getMsg());
        } catch (InfowayTimeoutException e) {
            System.err.println("超时: " + e.getMessage());
        } catch (InfowayIoException e) {
            System.err.println("网络: " + e.getMessage());
        } catch (InfowayApiException e) {
            // HTTP: RestErrorCode；WS: WsErrorCode。508–514 同号不同义。
            System.err.println(e.getMessage());
            System.err.printf("ret=%d name=%s msg=%s trace=%s%n",
                    e.getRet(), e.getErrorName(), e.getMsg(), e.getTraceId());
        }
    }
}
```

`InfowayRateLimitException` 继承 `InfowayApiException`。REST 限额约 **1200 次/分钟/Key**。HTTP 429、REST `ret` 501/502，或 body 为 `{"detail":"Rate limit exceeded"}` 时，SDK 会先退避再重试。

`getMessage()` 会带上码和枚举名，例如 REST `[508 PRODUCT_NOT_EXISTS] All product not exists`，WebSocket `[508 APIKEY_EXPIRED] …`。不要用 `WsErrorCode` 去解 REST 的 `ret`。

### REST `ret`（`RestErrorCode`）

| 码 | 名称 | 说明 |
|----|------|------|
| 200 | SUCCESS | 成功 |
| 400 | BAD_REQUEST | commonApi 参数错误（HTTP 也是 400） |
| 500 | SERVER_ERROR | 未捕获异常；或旧实现把业务错误写成了 500 |
| 501 / 502 | REQUEST_EXCEED_LIMIT / REQUEST_FOR_DAY_LIMIT | 限流 → `InfowayRateLimitException` |
| 503 | KLINE_EXCEEDS_LIMIT | K 线根数超限 |
| 505 | PRODUCTS_EXCEEDS_LIMIT | 品种数超限 |
| 506 / 507 | PARAM_ERROR / PARAM_LOST | 参数错误 / 缺失 |
| **508** | **PRODUCT_NOT_EXISTS** | **品种不存在**（不是 WS 的 key 过期） |
| 509 | TOKEN_PERMISSION_EXPIRED | token 权限过期 |
| 513 | TIME_LIMIT_ERROR | K 线 `timestamp` 超出历史深度 |
| **514** | **NO_PERMISSION** | **无该市场权限**（不是 WS 的 URL 错误） |

commonApi（`/common/basic/*`）只有 200 / 400 / 500。

伪造 Key 时：REST 抛 `InfowayAuthException` `[401] Token invalid`；行情 / 新闻 WebSocket 握手 HTTP 401，**不会重连**。

对照生产（2026-09-21）：行情业务错误目前仍是 **HTTP 200 + `ret=500`**，英文模板在 `msg` 里（`All product not exists`、`Param error：klineType`、`Timestamp limit error…`、`Kline quantity exceeds the limit：500`）。SDK 会带上 **message**。生产对部分财务过滤较松：`type=US` 仍可能按后缀返回 `AAPL.US` 行；`period_type=xx` 返回 HTTP 200 + 空列表；`getStockDetail(..., CRYPTO)` 是 HTTP 200 且 `data: null`。

### WebSocket `code`（`WsErrorCode`）

| 码 | 名称 | 说明 |
|----|------|------|
| 501 / 502 | REQUEST_FREQUENCY_MIN_EXCEED / DAY | 单连接 60 帧/分钟等 → `InfowayRateLimitException` |
| 505 / 516 | PRODUCTS_QUANTITY_EXCEED | 单连接 / 该 Key 全部连接 |
| 506 / 507 | PARAM_ERROR / PARAM_LOST | 参数错误或缺失 |
| **508–511** | **APIKEY_*** | **过期 / 无效 / 空 / 黑名单** |
| 512 / 513 / 514 | 连接超限 / 心跳超时 / URL 错误 | 513 后服务端会断开 |
| 515 | PARAM_NOT_JSON | 入站不是 JSON |
| 517–521 | 握手失败 | 缺 key / 无权限；519/520 在韩股、台股、新闻上含义不同 |

---

## 13. 完整示例

把 REST 快照和 WebSocket 推送放在同一个进程里：先拉一笔现价，再挂 15 秒实时成交。

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
            System.err.println("请先设置环境变量 INFOWAY_API_KEY");
            System.exit(1);
        }

        try (InfowayClient client = InfowayClient.builder().apiKey(apiKey).build()) {
            List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
            System.out.println("REST 现价: " + trades.get(0).price());

            System.out.println("公司资料: " + client.stockInfo().getCompany("AAPL.US", "zh-CN"));
            System.out.println("盈利状态: " + client.financial()
                    .getEarningStatus("AAPL.US", SymbolType.STOCK_US));
            System.out.println("日K: " + client.crypto().getKline("BTCUSDT", KlineType.DAY, 5));
        } catch (InfowayApiException e) {
            System.err.println(e.getMessage());
            return;
        }

        CountDownLatch firstTick = new CountDownLatch(1);
        InfowayWebSocket ws = InfowayWebSocket.builder()
                .apiKey(apiKey)
                .business("crypto")
                .onTrade((JsonObject data) -> {
                    System.out.println("WS 成交: " + data);
                    firstTick.countDown();
                })
                .onError(err -> System.err.println(err.getMessage()))
                .build();

        try {
            ws.connect();
            ws.subscribeTrade("BTCUSDT,ETHUSDT");
            if (!firstTick.await(45, TimeUnit.SECONDS)) {
                System.err.println("45 秒内没有成交推送");
            }
        } finally {
            ws.close();
        }
    }
}
```

运行：

```bash
export INFOWAY_API_KEY=你的密钥
javac -cp infoway-sdk-0.3.0.jar InfowayQuickstart.java
java -cp infoway-sdk-0.3.0.jar:. InfowayQuickstart
```

或在 Maven 项目里执行 `main`。实盘契约测试：

```bash
cd sdks/java
INFOWAY_LIVE_TESTS=1 INFOWAY_API_KEY=你的密钥 mvn test -Dtest=LiveContractTest
INFOWAY_LIVE_TESTS=1 INFOWAY_API_KEY=你的密钥 mvn test -Dtest=LiveDeepTest
```

---

## 附录：REST 路径一览

行情（`{market}` = `stock` / `crypto` / `japan` / `india` / `korea` / `taiwan` / `common`）：

- `GET /{market}/batch_trade/{codes}`
- `GET /{market}/batch_depth/{codes}`
- `POST /{market}/v2/batch_kline`

基础 / 财务：

- `GET /common/basic/symbols`
- `GET /common/basic/symbols/info`
- `GET /common/basic/symbols/adjustment_factors`
- `GET /common/basic/markets/trading_days`
- `GET /common/basic/markets/trading_schedule`
- `GET /common/basic/markets`
- `GET /common/basic/stock/detail`
- `GET /common/basic/financial/{earning_status|income_statement|revenue|cash_flow|balance_sheet|statistics|dividend|dividend_payout|earnings}`
- `GET /package/info`

市场 / 板块 / 个股：

- `GET /common/v2/basic/market/{temperature|indexes}`
- `GET /common/v2/basic/market/{breadth|turnover|leaders|overview|rank/categories}/{market}`
- `GET /common/v2/basic/market/rank/{market}/{key}`
- `GET /common/v2/basic/plate/{industry|concept|chart}/{market}`
- `GET /common/v2/basic/plate/{members|intro}/{plateSymbol}`
- `GET /common/v2/basic/stock/{valuation|ratings|company|panorama|concepts|events|drivers}/{symbol}`
