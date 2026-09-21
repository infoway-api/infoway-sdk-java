package io.infoway.sdk.model;

import java.time.Instant;
import java.util.List;

public record NewsItem(String dedupKey, String country, String lang, String route, String title,
                       Instant published, Integer urgency, String provider, List<String> symbols,
                       String link, String content, String summary) {}
