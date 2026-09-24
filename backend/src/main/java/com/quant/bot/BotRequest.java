package com.quant.bot;

import com.quant.strategy.StrategyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Map;

public record BotRequest(
        @NotBlank String name,
        @NotBlank String symbol,
        @NotNull StrategyType strategy,
        Map<String, Double> params,
        @NotNull @Positive BigDecimal notional,
        boolean enabled) {
}
