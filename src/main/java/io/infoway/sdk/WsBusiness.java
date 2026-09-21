package io.infoway.sdk;

/**
 * {@code business} query on {@code wss://data.infoway.io/ws}.
 *
 * <p>Must match the symbols you subscribe. A wrong channel is acked and then
 * stays silent.</p>
 */
public enum WsBusiness {

    STOCK("stock"),
    JAPAN("japan"),
    INDIA("india"),
    KOREA("korea"),
    TAIWAN("taiwan"),
    CRYPTO("crypto"),
    COMMON("common");

    private final String value;

    WsBusiness(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
