package com.quant.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        boolean enabled,
        boolean liveEnabled,
        BigDecimal maxOrderNotional,
        int maxOpenPositions,
        /** Also require manual confirmation on the paper account (live always requires it). */
        boolean approvalInPaper,
        /** Unconfirmed orders expire after this many minutes. */
        int approvalTtlMinutes) {
}
