package io.infoway.sdk.model;

import java.time.Instant;
import java.util.List;

public record Depth(String symbol, Instant time, List<DepthLevel> asks, List<DepthLevel> bids) {}
