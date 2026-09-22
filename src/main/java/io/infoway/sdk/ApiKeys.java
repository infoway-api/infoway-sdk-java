package io.infoway.sdk;

/**
 * Shared {@code INFOWAY_API_KEY} resolution for REST and WebSocket builders.
 */
final class ApiKeys {

    private ApiKeys() {}

    /**
     * {@code null} means the caller did not set a key, so the environment variable
     * is used. A blank string is an explicit empty key and is not replaced.
     */
    static String resolve(String apiKey) {
        if (apiKey != null) {
            return apiKey.trim();
        }
        String env = System.getenv("INFOWAY_API_KEY");
        return env != null ? env.trim() : "";
    }

    static String require(String apiKey) {
        String resolved = resolve(apiKey);
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "apiKey is required (set INFOWAY_API_KEY or call apiKey())");
        }
        return resolved;
    }
}
