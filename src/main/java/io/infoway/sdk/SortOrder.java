package io.infoway.sdk;

/** Rank list order ({@code order=asc|desc}). */
public enum SortOrder {

    ASC("asc"),
    DESC("desc");

    private final String value;

    SortOrder(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
