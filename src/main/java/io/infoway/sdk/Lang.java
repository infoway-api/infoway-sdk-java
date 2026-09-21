package io.infoway.sdk;

/**
 * REST language for name-like fields ({@code lang=en|zh-CN}).
 *
 * <p>News WebSocket uses {@link NewsLang} instead ({@code zh-Hans}/{@code zh-Hant}).</p>
 */
public enum Lang {

    EN("en"),
    ZH_CN("zh-CN");

    private final String value;

    Lang(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
