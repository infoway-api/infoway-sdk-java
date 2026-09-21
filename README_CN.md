# Infoway Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.infoway/infoway-sdk.svg)](https://search.maven.org/artifact/io.infoway/infoway-sdk)
[![Java](https://img.shields.io/badge/Java-21%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

[English](README.md) | **中文**

[Infoway](https://infoway.io) 实时金融数据 API 的官方 Java SDK，支持港股、美股、A股、日股、韩股、印股、台股、加密货币以及通用行情的 REST 和 WebSocket 接口。

完整示例与分接口用法见 [使用说明](USAGE_CN.md)，英文对照 [USAGE.md](USAGE.md)。

> **请升级到 0.3.0。** 此前版本（包括旧文档里写的 0.1.0）会把大部分错误响应当成成功、静默返回 `null`，
> 且 `basic()` 五个方法的查询参数全部发错。升级前请看 [0.3.0 变更说明](#030-变更说明) 和
> [0.2.0](#020-变更说明)，`basic()` 签名在 0.2.0 有破坏性变更。

## 安装

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

## 快速开始

```java
import io.infoway.sdk.InfowayClient;
import io.infoway.sdk.KlineType;
import com.google.gson.JsonElement;

// 创建客户端
InfowayClient client = InfowayClient.builder()
    .apiKey("YOUR_API_KEY")
    .build();

// 实时行情
JsonElement trades = client.stock().getTrade("AAPL.US");
System.out.println(trades);

// K线数据
JsonElement klines = client.crypto().getKline("BTCUSDT", KlineType.DAY, 100);

// 市场温度
JsonElement temp = client.market().getTemperature("HK,US");

// 板块数据
JsonElement industry = client.plate().getIndustry("HK", 10);

// 个股信息
JsonElement company = client.stockInfo().getCompany("AAPL.US");

// 当前 Key 的套餐额度
JsonElement pkg = client.packages().getInfo();

// 释放资源
client.close();
```

## REST API

### 行情数据

适用于所有市场：`client.stock()`、`client.crypto()`、`client.japan()`、`client.india()`、`client.korea()`、`client.taiwan()`、`client.common()`。

| 方法 | 说明 |
|------|------|
| `getTrade(codes)` | 实时成交数据 |
| `getDepth(codes)` | 盘口深度 |
| `getKline(codes, type, count)` | K线数据 |
| `getKline(codes, type, count, timestamp)` | 同上，截止到指定秒级时间戳（分钟/小时 K） |

### 基础信息

```java
// type 取值：STOCK_US STOCK_CN STOCK_HK STOCK_JP STOCK_KS STOCK_IN STOCK_TW
// CRYPTO FOREX FUTURES ENERGY METAL INDICES
client.basic().getSymbols(SymbolType.STOCK_US);              // 标的列表
client.basic().getSymbols("STOCK_US", "AAPL.US,TSLA.US");    // 指定代码
client.basic().getSymbolInfo("STOCK_US", "AAPL.US");         // 标的详情
client.basic().getStockDetail(SymbolType.STOCK_US, "AAPL.US"); // 个股档案
client.basic().getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815"); // 复权因子
client.basic().getTradingDays("US", "20260801", "20260815"); // 交易日历
client.basic().getTradingSchedule();                         // 交易时段/假期（无市场过滤）
client.basic().getTradingScheduleByType(ScheduleType.ENERGY); // ENERGY / FOREX / FUTURES / METAL / INDICES
client.basic().getMarkets();                                 // 各市场交易时间表
client.packages().getInfo();                                 // 套餐：packageName、expireTime、apiNumPerSec …
```

日期一律用 `YYYYMMDD` 字符串。上面每个参数服务端都必填：给 `getSymbols` 传 `market=US`
会得到 HTTP 400 `Required parameter 'type' is not present.`
`getTradingSchedule("US")` 与无参相同。给 `getTradingScheduleByType` 传 `SymbolType.STOCK_US`
会在发请求前抛 `IllegalArgumentException`。

### 市场概览

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

### 板块

```java
client.plate().getIndustry("HK", 200);     // 行业板块
client.plate().getConcept("HK", 100);      // 概念板块
client.plate().getMembers("IN20293.HK", 0, 50); // 板块成分股
client.plate().getIntro("IN20293.HK");     // 板块简介
client.plate().getChart("HK", 50);         // 板块图表
```

### 个股信息

```java
client.stockInfo().getValuation("AAPL.US"); // 估值
client.stockInfo().getRatings("AAPL.US");   // 分析师评级
client.stockInfo().getCompany("AAPL.US");   // 公司信息
client.stockInfo().getPanorama("AAPL.US");   // 全景
client.stockInfo().getConcepts("AAPL.US");   // 概念
client.stockInfo().getEvents("AAPL.US", 20); // 事件
client.stockInfo().getDrivers("AAPL.US");    // 驱动因素
client.stockInfo().getCompany("00700.HK", "zh-CN"); // 可选 lang=en|zh-CN
```

### 财务数据

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

## WebSocket 实时推送

```java
import io.infoway.sdk.InfowayWebSocket;

InfowayWebSocket ws = InfowayWebSocket.builder()
    .apiKey("YOUR_API_KEY")                // 可省略，读 INFOWAY_API_KEY
    .business(WsBusiness.CRYPTO)           // STOCK | JAPAN | INDIA | KOREA | TAIWAN | CRYPTO | COMMON
    .printFrames(true)                     // 可选；默认 false，帧只写 DEBUG
    .onTrade(data -> System.out.println(data.get("s") + " " + data.get("p")))
    .onDepth(data -> System.out.println("盘口: " + data))
    .onKline(data -> System.out.println("K线: " + data))
    .onError(err -> System.err.println("错误: " + err.getMessage()))
    .onReconnect(() -> System.out.println("已重连"))
    .build();

ws.connect();
ws.subscribeTrade("BTCUSDT,ETHUSDT");      // 多个代码合并成一条订阅
ws.subscribeTrade("AAPL.US", true);        // includeTy=true 时股票推送交易类型 ty
ws.subscribeKline("BTCUSDT", KlineType.MIN_1);

// 稍后...
ws.unsubscribeKline("BTCUSDT", KlineType.MIN_1);
ws.close();
```

回调收到的是 **`data` 本身**，与 REST 对齐：成交推送直接是 `{"s","p","v","vw","t","td"}`，
不再是外层的 `{"code":10002,"data":{…}}`。

以下都是服务端的真实行为，不是 SDK 的 bug：

| 现象 | 说明 |
|------|------|
| 首帧是纯文本 `You have permission to subscribe to all market data` | `business=stock` 连上就会推。SDK 会跳过非 JSON 帧。 |
| `{"code":200,"msg":"ws connect success"}` | 欢迎帧，不是错误。 |
| 回了 `{"code":10001,"msg":"ok"}` 之后一直没数据 | ack 只代表"请求被接受"。business 选错（比如在 `crypto` 上订 `AAPL.US`）、代码不存在、休市，都会 ack 然后零推送。请按市场选对 business。 |
| 心跳没有任何应答 | 服务端对 `10010` 不回包。**不要**用"等心跳 ack 超时"判断断线，会触发重连风暴。 |
| 发了很多帧后被断开 | 单连接 **60 次/分钟**（订阅+退订+心跳合计）。务必把代码合并到一个逗号串里。 |
| 连接时 HTTP 401 | key 无效，或该 key 没有这个频道的权限。SDK 会抛 `InfowayAuthException` 并**停止重连**，避免持续冲 401 被判恶意。 |
| 第一次连上就进 `onReconnect` | 只在**再次**连上时触发。`onDisconnect` 只表示意外掉线——`close()` 不触发它，并会打断退避。 |
| 退订后重连又订回来 | 客户端保存的是**目标订阅集**。已退订的内容不会回放；K 线可按单个周期退订。 |

### 新闻频道

新闻是独立端点（`wss://data.infoway.io/news`），需要单独授权：

```java
import io.infoway.sdk.InfowayNewsWebSocket;

InfowayNewsWebSocket news = InfowayNewsWebSocket.builder()
    .apiKey("YOUR_API_KEY")
    .lang(NewsLang.ZH_HANS)                // EN ZH_HANS ZH_HANT JA KO …
    .printFrames(true)                     // 可选；默认 false
    .onNews(item -> System.out.println(item.get("title").getAsString()))
    .onNewsParsed(item -> System.out.println(item.title()))
    .onError(err -> System.err.println(err.getMessage()))
    .build();

news.connect();
news.unsubscribe();                        // 协议号 11020
// 稍后
news.close();
```

推送字段：`dk`（去重键）、`country`、`lang`、`route`、`title`、`published`（**秒**级时间戳）、
`urgency`（越小越急）、`provider`、`symbols[]`、`link`、`content`、`sd`（摘要）。
再次订阅即覆盖语言；`unsubscribe()` 发送 11020。一个 key 只允许一条新闻连接。
没有新闻权限的 key 会在握手阶段收到 HTTP 401。

### 类型化模型（可选）

默认仍然返回原始 `JsonElement`；需要 SDK 帮你吸收服务端的字段差异时：

```java
import io.infoway.sdk.model.*;

List<Trade> trades = client.crypto().getTradeParsed("BTCUSDT");
List<Depth> books  = client.crypto().getDepthParsed("BTCUSDT");
List<Kline> bars   = client.crypto().getKlineParsed("BTCUSDT", KlineType.MIN_1, 100);

ws.setOnKline(data -> handle(Normalizer.kline(data)));
```

| 服务端实况 | 归一化后 |
|-----------|---------|
| 价格/成交量是字符串（`"305.771"`） | `BigDecimal` |
| `t`：成交/盘口是数字**毫秒**，K线是字符串**秒** | `Instant`（按字段判断单位，不按位数猜） |
| 涨跌幅 REST 叫 `pc`、WS 叫 `pfr`，值为 `"0.03%"` | `changePercent` = `0.0003` |
| K线多套一层 `respList` | 展平为列表 |
| 盘口 `a`/`b` 是转置列式 `[[价格…],[数量…]]` | `List<DepthLevel>`，即 `(price, quantity)` |
| `vw` 实为**成交额**（英文文档写反成 VWAP） | `turnover` |

### WebSocket 消息码

客户端 → 服务器：

| 码值 | 名称 | 说明 |
|------|------|------|
| 10000 | SUB_TRADE | 订阅成交 |
| 10003 | SUB_DEPTH | 订阅盘口 |
| 10006 | SUB_KLINE | 订阅K线（payload `data.arr=[{codes, type}]`）|
| 10010 | HEARTBEAT | 心跳保活（带 `ack=1` 时服务端回 10011） |
| 11000 | UNSUB_TRADE | 取消订阅成交 |
| 11001 | UNSUB_DEPTH | 取消订阅盘口 |
| 11002 | UNSUB_KLINE | 取消订阅K线 |
| 10020 | SUB_NEWS | 订阅新闻（走 `/news` 端点）|
| 11020 | UNSUB_NEWS | 取消订阅新闻 |

服务器 → 客户端：

| 码值 | 名称 | 说明 |
|------|------|------|
| 10001 | SUB_TRADE_ACK | 成交订阅确认 |
| 10002 | PUSH_TRADE | **实时成交推送** |
| 10004 | SUB_DEPTH_ACK | 盘口订阅确认 |
| 10005 | PUSH_DEPTH | **实时盘口推送** |
| 10007 | SUB_KLINE_ACK | K线订阅确认 |
| 10008 | PUSH_KLINE | **实时K线推送** |
| 10011 | HEART_APPLY | 心跳确认（仅当客户端传了 `ack=1`） |
| 11010 | UNSUB_ACK | 取消订阅确认（成交/盘口/K线/新闻共用） |
| 10021 | SUB_NEWS_ACK | 新闻订阅确认 |
| 10022 | PUSH_NEWS | **实时新闻推送** |
| 200 | — | 欢迎帧 `{"code":200,"msg":"ws connect success"}` |

服务端错误帧（`{"code":5xx,"msg":"...","traceId":"..."}`）一律进 `onError`。`feat/stock` 有时会在 JSON 前加 `Subscribe fail:`，SDK 会剥掉再解析。

| 码值 | 名称 | 说明 |
|------|------|------|
| 500 | SERVER_ERROR | 服务异常 |
| 501 / 502 | 频率超限 | 按 `InfowayRateLimitException`（501 为每分钟 60 帧） |
| 505 / 516 | 订阅产品数超限 | 单连接 / 该 key 全部连接 |
| 506 / 507 | PARAM_ERROR / PARAM_LOST | 参数错误或缺失 |
| 508–511 | API Key | 过期 / 无效 / 空 / 黑名单 |
| 512 / 513 / 514 | 连接 / 心跳超时 / URL | 513 后服务端会断开 |
| 515 | PARAM_NOT_JSON | 入站不是 JSON（stock 有时发纯文本） |
| 517–521 | 握手失败 | 缺 key / 无权限等；519/520 在韩股、台股、新闻上含义不同 |

### K线类型

| 枚举 | 值 | 说明 |
|------|-----|------|
| MIN_1 | 1 | 1分钟 |
| MIN_5 | 2 | 5分钟 |
| MIN_15 | 3 | 15分钟 |
| MIN_30 | 4 | 30分钟 |
| HOUR_1 | 5 | 1小时 |
| HOUR_2 | 6 | 2小时 |
| HOUR_4 | 7 | 4小时 |
| DAY | 8 | 日线 |
| WEEK | 9 | 周线 |
| MONTH | 10 | 月线 |
| QUARTER | 11 | 季线 |
| YEAR | 12 | 年线 |

## 配置

| Builder 方法 | 默认值 | 说明 |
|-------------|--------|------|
| `apiKey(key)` | `INFOWAY_API_KEY` 环境变量 | API 密钥 |
| `baseUrl(url)` | `https://data.infoway.io` | 基础 URL |
| `timeout(secs)` | `15` | 请求超时（秒） |
| `maxRetries(n)` | `3` | 最大重试次数 |

## 错误处理

```java
import io.infoway.sdk.exception.*;

try {
    client.stock().getTrade("INVALID");
} catch (InfowayAuthException e) {
    // 401 未授权（或 ret=401）
    System.err.println("认证失败: " + e.getMsg());
} catch (InfowayRateLimitException e) {
    // HTTP 429、REST ret 501/502，或 HTTP 200 + {"detail":"Rate limit exceeded"}
    System.err.println("触发限流 [" + e.getRet() + " " + e.getErrorName() + "]: " + e.getMsg());
} catch (InfowayApiException e) {
    // getErrorName()：HTTP 用 RestErrorCode，WS 用 WsErrorCode（508–514 同号不同义）
    System.err.println(e.getMessage());
    System.err.println("Trace ID: " + e.getTraceId());
} catch (InfowayTimeoutException e) {
    // 请求超时
    System.err.println("超时: " + e.getMessage());
} catch (InfowayIoException e) {
    // 重试耗尽（网络 / I/O）
    System.err.println("网络: " + e.getMessage());
}
```

`InfowayRateLimitException` 继承自 `InfowayApiException`，原有的 catch 块不受影响。
`getMessage()` 在 REST 上类似 `[508 PRODUCT_NOT_EXISTS] All product not exists`，在 WebSocket
上是 `[508 APIKEY_EXPIRED] …`（同号不同枚举：`RestErrorCode` / `WsErrorCode`）。
不要用 `WsErrorCode` 去解 REST 的 `ret`。REST 限流为 **1200 次/分钟/key**；K 线单产品最多 500 根，
多产品请求会被服务端静默截断到每个 2 根。伪造 Key：REST 抛 `InfowayAuthException` `[401] Token invalid`，
行情 / 新闻 WebSocket 握手 HTTP 401 且**不重连**。

REST `ret`（`RestErrorCode`）：200 成功；400 commonApi 参数错误；500 未捕获异常，**或**生产行情
业务错误仍写成 500 + 英文 `msg`；501/502 限流；503 K 线根数超限；505 品种数超限；506/507 参数错误/缺失；
**508 `PRODUCT_NOT_EXISTS`**；509 token 权限过期；513 K 线历史超出套餐；**514 `NO_PERMISSION`**。
`/common/basic/*` 只有 200 / 400 / 500。对照生产（2026-09-21）行情错误仍是 HTTP 200 + `ret=500`，
enum 文案在 `msg`。

## 0.3.0 变更说明

- 当前 Maven 坐标为 `io.infoway:infoway-sdk:0.3.0`。
- `packages().getInfo()`、`getTradingScheduleByType(ScheduleType)`、`InfowayIoException`。
- `RestErrorCode` / `WsErrorCode` / `getErrorName()`；REST 508 是 `PRODUCT_NOT_EXISTS`，WS 508 是 `APIKEY_EXPIRED`。
- 枚举参数：`Market`、`Lang`、`NewsLang`、`PeriodType`、`WsBusiness`、`RankSort`、`SortOrder`、`ScheduleType`。
- `printFrames` 默认 false（只写 DEBUG）。REST 和 WebSocket 都读 `INFOWAY_API_KEY`。
- 新闻 `onNewsParsed`。韩股 / 台股 / 财务客户端。

## 0.2.0 变更说明

**修复**

- 错误响应不再被吞掉。此前只要 body 里没有 `ret`/`code`（HTTP 400 problem+json、404、429、
  `{"detail":"Rate limit exceeded"}`）就会被判成功并返回 `null`；网关 HTML 页会裸抛
  `JsonSyntaxException`，502 空 body 会裸抛 `NullPointerException`。
- 没有 `data` 键的响应（如 `plate().getIntro(...)`）现在返回整个 body，而不是 `null`。
- WebSocket 回调改为传 `data`，与 REST 对齐；此前 `msg.get("p")` 恒为 `null`。
- `unsubscribeKline` 现在发送 `klineTypes`；此前会导致服务端清掉该产品的**所有**周期。
- 连接建立前发起的订阅不再被静默丢弃。
- key 无效时抛 `InfowayAuthException` 并停止重连，不再无限重连。
- 服务端错误帧（如 `506`/`507`）会回调 `onError`，不再只打 debug 日志后丢弃。

**破坏性变更** —— `basic()` 五个方法此前发送的参数服务端根本不认，100% 返回 `null`：

| 旧写法 | 新写法 |
|--------|--------|
| `getSymbols("US")` | `getSymbols("STOCK_US")` / `getSymbols(SymbolType.STOCK_US)` |
| `getSymbolInfo("AAPL.US")` | `getSymbolInfo("STOCK_US", "AAPL.US")` |
| `getAdjustmentFactors("AAPL.US")` | `getAdjustmentFactors("AAPL.US", "US", "20260801", "20260815")` |
| `getTradingDays("US")` | `getTradingDays("US", "20260801", "20260815")` |
| `getTradingHours("US")` | `getTradingSchedule("US")`（旧名保留为 deprecated 别名） |

**新增**

- `InfowayNewsWebSocket`：`/news` 新闻频道（`onNews` / `onNewsParsed`）。
- `io.infoway.sdk.model`：`Trade`/`Depth`/`Kline`/`NewsItem` 与 `Normalizer`，
  以及各行情客户端上的 `getTradeParsed` / `getDepthParsed` / `getKlineParsed`。
- `InfowayRateLimitException`、`SymbolType`。字符串重载仍可用。

## 环境要求

- Java 21+
- 依赖：OkHttp 4.x、Gson、SLF4J

## 资源

- 官网：[https://infoway.io](https://infoway.io)
- API 文档：[https://docs.infoway.io](https://docs.infoway.io)
- 免费试用：[7天免费试用](https://infoway.io)

## 许可证

MIT
