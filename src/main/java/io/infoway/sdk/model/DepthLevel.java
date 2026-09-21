package io.infoway.sdk.model;

import java.math.BigDecimal;

public record DepthLevel(BigDecimal price, BigDecimal quantity) {}
