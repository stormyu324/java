package com.quant.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trading")
public record TradingProperties(
        boolean enabled,
        boolean liveEnabled,
        BigDecimal maxOrderNotional,
        int maxOpenPositions) {
}
