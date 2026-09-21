package io.infoway.sdk.rest;

import java.util.LinkedHashMap;
import java.util.Map;

/** Shared query-string builder. Omits null / empty values. */
final class Query {

    private Query() {}

    static Map<String, String> of(String... keyValues) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            put(params, keyValues[i], keyValues[i + 1]);
        }
        return params.isEmpty() ? null : params;
    }

    static void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isEmpty()) {
            params.put(key, value);
        }
    }

    static void put(Map<String, String> params, String key, Integer value) {
        if (value != null) {
            params.put(key, String.valueOf(value));
        }
    }
}
