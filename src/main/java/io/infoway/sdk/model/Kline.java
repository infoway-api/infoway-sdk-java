package io.infoway.sdk.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Kline(String symbol, Instant time, BigDecimal open, BigDecimal high, BigDecimal low,
                    BigDecimal close, BigDecimal volume, BigDecimal turnover,
                    BigDecimal changeAmount, BigDecimal changePercent, Integer klineType) {}
