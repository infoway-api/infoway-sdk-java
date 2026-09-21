package io.infoway.sdk;

/**
 * Language for {@link InfowayNewsWebSocket} ({@code data.lang}).
 */
public enum NewsLang {

    EN("en"),
    ZH_HANS("zh-Hans"),
    ZH_HANT("zh-Hant"),
    JA("ja"),
    KO("ko"),
    DE("de"),
    FR("fr"),
    ES("es"),
    PT("pt"),
    RU("ru"),
    TR("tr");

    private final String value;

    NewsLang(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
