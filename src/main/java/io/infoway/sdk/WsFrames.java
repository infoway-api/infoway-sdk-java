package io.infoway.sdk;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.infoway.sdk.exception.InfowayApiException;
import io.infoway.sdk.exception.InfowayRateLimitException;

/**
 * Shared parse / classify for inbound WebSocket text frames.
 */
final class WsFrames {

    private static final String SUBSCRIBE_FAIL_PREFIX = "Subscribe fail:";

    private WsFrames() {}

    /**
     * Parse a text frame into a JSON object.
     *
     * <p>Stock {@code MessageHandler} sometimes prefixes a valid error JSON with
     * {@code Subscribe fail:}. Permission greetings and other plain text return
     * {@code null} so callers can ignore them.</p>
     */
    static JsonObject parseObject(Gson gson, String text) {
        if (text == null) {
            return null;
        }
        String body = text.trim();
        if (body.startsWith(SUBSCRIBE_FAIL_PREFIX)) {
            body = body.substring(SUBSCRIBE_FAIL_PREFIX.length()).trim();
        }
        int brace = body.indexOf('{');
        if (brace > 0) {
            body = body.substring(brace);
        }
        if (body.isEmpty() || body.charAt(0) != '{') {
            return null;
        }
        try {
            JsonElement parsed = gson.fromJson(body, JsonElement.class);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    static Exception toError(JsonObject msg, String rawText) {
        int code = msg.get("code").getAsInt();
        String msgText = msg.has("msg") && msg.get("msg").isJsonPrimitive()
                ? msg.get("msg").getAsString()
                : rawText;
        String traceId = firstTrace(msg);
        if (code == WsErrorCode.REQUEST_FREQUENCY_MIN_EXCEED.getCode()
                || code == WsErrorCode.REQUEST_FREQUENCY_DAY_EXCEED.getCode()) {
            return InfowayRateLimitException.ws(code, msgText, traceId);
        }
        return InfowayApiException.ofWs(code, msgText, traceId);
    }

    private static String firstTrace(JsonObject msg) {
        if (msg.has("traceId") && msg.get("traceId").isJsonPrimitive()) {
            return msg.get("traceId").getAsString();
        }
        if (msg.has("trace") && msg.get("trace").isJsonPrimitive()) {
            return msg.get("trace").getAsString();
        }
        return null;
    }
}
