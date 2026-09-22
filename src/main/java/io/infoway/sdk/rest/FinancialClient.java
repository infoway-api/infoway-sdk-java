package io.infoway.sdk.rest;

import com.google.gson.JsonElement;
import io.infoway.sdk.HttpClient;
import io.infoway.sdk.PeriodType;
import io.infoway.sdk.RestErrorCode;
import io.infoway.sdk.SymbolType;
import io.infoway.sdk.exception.InfowayApiException;

/**
 * Stock financial statements and earnings.
 *
 * <p>Nine endpoints under {@code /common/basic/financial/*}. Every call needs
 * {@code symbol} plus a {@link SymbolType} (including {@link SymbolType#STOCK_TW}).
 * Statement-style methods accept an optional {@code period_type}:
 * {@code fq} (quarter), {@code fy} (year), {@code fh} (half-year).</p>
 */
public class FinancialClient {

    private final HttpClient http;

    public FinancialClient(HttpClient http) {
        this.http = http;
    }

    /** Latest reported period vs the next expected print. */
    public JsonElement getEarningStatus(String symbol, SymbolType type) {
        return getEarningStatus(symbol, typeValue(type));
    }

    public JsonElement getEarningStatus(String symbol, String type) {
        return get("/common/basic/financial/earning_status", symbol, type, null);
    }

    public JsonElement getIncomeStatement(String symbol, SymbolType type) {
        return getIncomeStatement(symbol, typeValue(type), null);
    }

    public JsonElement getIncomeStatement(String symbol, String type) {
        return getIncomeStatement(symbol, type, null);
    }

    public JsonElement getIncomeStatement(String symbol, String type, String periodType) {
        return get("/common/basic/financial/income_statement", symbol, type, periodType);
    }

    public JsonElement getIncomeStatement(String symbol, SymbolType type, PeriodType periodType) {
        return getIncomeStatement(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    public JsonElement getRevenue(String symbol, SymbolType type) {
        return getRevenue(symbol, typeValue(type), null);
    }

    public JsonElement getRevenue(String symbol, String type) {
        return getRevenue(symbol, type, null);
    }

    public JsonElement getRevenue(String symbol, String type, String periodType) {
        return get("/common/basic/financial/revenue", symbol, type, periodType);
    }

    public JsonElement getRevenue(String symbol, SymbolType type, PeriodType periodType) {
        return getRevenue(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    public JsonElement getCashFlow(String symbol, SymbolType type) {
        return getCashFlow(symbol, typeValue(type), null);
    }

    public JsonElement getCashFlow(String symbol, String type) {
        return getCashFlow(symbol, type, null);
    }

    public JsonElement getCashFlow(String symbol, String type, String periodType) {
        return get("/common/basic/financial/cash_flow", symbol, type, periodType);
    }

    public JsonElement getCashFlow(String symbol, SymbolType type, PeriodType periodType) {
        return getCashFlow(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    public JsonElement getBalanceSheet(String symbol, SymbolType type) {
        return getBalanceSheet(symbol, typeValue(type), null);
    }

    public JsonElement getBalanceSheet(String symbol, String type) {
        return getBalanceSheet(symbol, type, null);
    }

    public JsonElement getBalanceSheet(String symbol, String type, String periodType) {
        return get("/common/basic/financial/balance_sheet", symbol, type, periodType);
    }

    public JsonElement getBalanceSheet(String symbol, SymbolType type, PeriodType periodType) {
        return getBalanceSheet(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    public JsonElement getStatistics(String symbol, SymbolType type) {
        return getStatistics(symbol, typeValue(type), null);
    }

    public JsonElement getStatistics(String symbol, String type) {
        return getStatistics(symbol, type, null);
    }

    public JsonElement getStatistics(String symbol, String type, String periodType) {
        return get("/common/basic/financial/statistics", symbol, type, periodType);
    }

    public JsonElement getStatistics(String symbol, SymbolType type, PeriodType periodType) {
        return getStatistics(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    public JsonElement getDividend(String symbol, SymbolType type) {
        return getDividend(symbol, typeValue(type), null);
    }

    public JsonElement getDividend(String symbol, String type) {
        return getDividend(symbol, type, null);
    }

    public JsonElement getDividend(String symbol, String type, String periodType) {
        return get("/common/basic/financial/dividend", symbol, type, periodType);
    }

    public JsonElement getDividend(String symbol, SymbolType type, PeriodType periodType) {
        return getDividend(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    public JsonElement getDividendPayout(String symbol, SymbolType type) {
        return getDividendPayout(symbol, typeValue(type));
    }

    public JsonElement getDividendPayout(String symbol, String type) {
        return get("/common/basic/financial/dividend_payout", symbol, type, null);
    }

    public JsonElement getEarnings(String symbol, SymbolType type) {
        return getEarnings(symbol, typeValue(type), null);
    }

    public JsonElement getEarnings(String symbol, String type) {
        return getEarnings(symbol, type, null);
    }

    public JsonElement getEarnings(String symbol, String type, String periodType) {
        return get("/common/basic/financial/earnings", symbol, type, periodType);
    }

    public JsonElement getEarnings(String symbol, SymbolType type, PeriodType periodType) {
        return getEarnings(symbol, typeValue(type), periodType != null ? periodType.value() : null);
    }

    private JsonElement get(String path, String symbol, String type, String periodType) {
        if (SymbolType.fromValue(type) == null) {
            throw InfowayApiException.ofRest(
                    RestErrorCode.PARAM_ERROR.getCode(),
                    "Param error：type",
                    null);
        }
        return http.get(path, Query.of(
                "symbol", symbol,
                "type", type,
                "period_type", periodType));
    }

    private static String typeValue(SymbolType type) {
        return type != null ? type.value() : null;
    }
}
