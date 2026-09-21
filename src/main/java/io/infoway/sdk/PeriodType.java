package io.infoway.sdk;

/**
 * Financial statement period ({@code period_type}).
 */
public enum PeriodType {

    /** Quarterly. */
    FQ("fq"),
    /** Annual. */
    FY("fy"),
    /** Semi-annual / half-year. */
    FH("fh");

    private final String value;

    PeriodType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
