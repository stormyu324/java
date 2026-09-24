package com.quant.backtest;

import com.quant.strategy.StrategyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.Map;

public record BacktestRequest(
        @NotBlank String symbol,
        @NotNull StrategyType strategy,
        Map<String, Double> params,
        @NotNull LocalDate from,
        @NotNull LocalDate to,
        @Positive double initialCapital,
        /** Commission per trade side, in basis points of traded value. */
        @DecimalMin("0") @DecimalMax("100") double commissionBps,
        /** Adverse price slippage per fill, in basis points. */
        @DecimalMin("0") @DecimalMax("100") double slippageBps) {
}
