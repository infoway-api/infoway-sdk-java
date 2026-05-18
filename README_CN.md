# Infoway Java SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.infoway/infoway-sdk.svg)](https://search.maven.org/artifact/io.infoway/infoway-sdk)
[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

[English](README.md) | **中文**

[Infoway](https://infoway.io) 实时金融数据 API 的官方 Java SDK，支持港股、美股、A股、加密货币、日本及印度市场的 REST 和 WebSocket 接口。

## 安装

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

// 释放资源
client.close();
```

## REST API

### 行情数据

适用于所有市场：`client.stock()`、`client.crypto()`、`client.japan()`、`client.india()`、`client.common()`。

| 方法 | 说明 |
|------|------|
| `getTrade(codes)` | 实时成交数据 |
| `getDepth(codes)` | 盘口深度 |
| `getKline(codes, type, count)` | K线数据 |

### 基础信息

```java
client.basic().getSymbols("US");           // 标的列表
client.basic().getSymbolInfo("AAPL.US");   // 标的详情
client.basic().getTradingDays("US");       // 交易日历
client.basic().getTradingHours("US");      // 交易时间
client.basic().getAdjustmentFactors("AAPL.US"); // 复权因子
```

### 市场概览

```java
client.market().getTemperature("HK,US");   // 市场温度
client.market().getBreadth("US");          // 涨跌统计
client.market().getIndexes();              // 主要指数
client.market().getLeaders("US", 10);      // 领涨领跌
client.market().getRankConfig("US");       // 排行配置
```

### 板块

```java
client.plate().getIndustry("HK", 200);     // 行业板块
client.plate().getConcept("HK", 100);      // 概念板块
client.plate().getMembers("BK1001", 0, 50); // 板块成分股
client.plate().getIntro("BK1001");         // 板块简介
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
```

## WebSocket 实时推送

```java
import io.infoway.sdk.InfowayWebSocket;

InfowayWebSocket ws = InfowayWebSocket.builder()
    .apiKey("YOUR_API_KEY")
    .business("stock")
    .onTrade(msg -> System.out.println("成交: " + msg))
    .onDepth(msg -> System.out.println("盘口: " + msg))
    .onKline(msg -> System.out.println("K线: " + msg))
    .onError(err -> System.err.println("错误: " + err.getMessage()))
    .onReconnect(() -> System.out.println("已重连"))
    .build();

ws.connect();
ws.subscribeTrade("AAPL.US,TSLA.US");
ws.subscribeDepth("AAPL.US");

// 稍后...
ws.unsubscribeTrade("TSLA.US");
ws.close();
```

### WebSocket 消息码

客户端 → 服务器：

| 码值 | 名称 | 说明 |
|------|------|------|
| 10000 | SUB_TRADE | 订阅成交 |
| 10003 | SUB_DEPTH | 订阅盘口 |
| 10006 | SUB_KLINE | 订阅K线（payload `data.arr=[{codes, type}]`）|
| 10010 | HEARTBEAT | 心跳保活 |
| 11000 | UNSUB_TRADE | 取消订阅成交 |
| 11001 | UNSUB_DEPTH | 取消订阅盘口 |
| 11002 | UNSUB_KLINE | 取消订阅K线 |

服务器 → 客户端：

| 码值 | 名称 | 说明 |
|------|------|------|
| 10001 | SUB_TRADE_ACK | 成交订阅确认 |
| 10002 | PUSH_TRADE | **实时成交推送** |
| 10004 | SUB_DEPTH_ACK | 盘口订阅确认 |
| 10005 | PUSH_DEPTH | **实时盘口推送** |
| 10007 | SUB_KLINE_ACK | K线订阅确认 |
| 10008 | PUSH_KLINE | **实时K线推送** |
| 11010 | UNSUB_ACK | 取消订阅确认 |

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
    // 401 未授权
    System.err.println("认证失败: " + e.getMsg());
} catch (InfowayApiException e) {
    // 其他 API 错误
    System.err.println("API 错误 [" + e.getRet() + "]: " + e.getMsg());
    System.err.println("Trace ID: " + e.getTraceId());
} catch (InfowayTimeoutException e) {
    // 请求超时
    System.err.println("超时: " + e.getMessage());
}
```

## 环境要求

- Java 17+
- 依赖：OkHttp 4.x、Gson、SLF4J

## 资源

- 官网：[https://infoway.io](https://infoway.io)
- API 文档：[https://docs.infoway.io](https://docs.infoway.io)
- 免费试用：[7天免费试用](https://infoway.io)

## 许可证

MIT
