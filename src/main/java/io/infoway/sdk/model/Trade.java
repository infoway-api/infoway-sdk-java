package io.infoway.sdk.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Trade(String symbol, Instant time, BigDecimal price, BigDecimal volume,
                    BigDecimal turnover, int direction, String tradeType) {}
