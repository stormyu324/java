package com.quant.trading;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank String symbol,
        @NotNull Side side,
        @NotNull @Positive BigDecimal qty,
        @NotNull Type type,
        @Positive BigDecimal limitPrice,
        TimeInForce timeInForce) {

    public enum Side { BUY, SELL }

    public enum Type { MARKET, LIMIT }

    public enum TimeInForce { DAY, GTC }
}
