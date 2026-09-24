package com.quant.trading;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;

/** Subsets of Alpaca's trading API objects (snake_case in and out). */
public final class BrokerModels {

    private BrokerModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Account(String accountNumber, String status, String currency, BigDecimal cash, BigDecimal equity,
                          BigDecimal lastEquity, BigDecimal buyingPower, BigDecimal portfolioValue,
                          boolean tradingBlocked, boolean patternDayTrader) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Position(String symbol, BigDecimal qty, String side, BigDecimal avgEntryPrice,
                           BigDecimal currentPrice, BigDecimal marketValue, BigDecimal costBasis,
                           BigDecimal unrealizedPl, BigDecimal unrealizedPlpc, BigDecimal changeToday) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Order(String id, String clientOrderId, String symbol, BigDecimal qty, BigDecimal notional,
                        BigDecimal filledQty, BigDecimal filledAvgPrice, String side, String type,
                        String timeInForce, BigDecimal limitPrice, String status, Instant submittedAt,
                        Instant filledAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Clock(Instant timestamp, boolean isOpen, Instant nextOpen, Instant nextClose) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record NewOrder(String symbol, String qty, String side, String type, String timeInForce,
                           String limitPrice, String clientOrderId) {
    }
}
