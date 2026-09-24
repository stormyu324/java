package com.quant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alpaca")
public record AlpacaProperties(String keyId, String secretKey, boolean paper, String dataUrl, String feed) {

    public static final String PAPER_URL = "https://paper-api.alpaca.markets";
    public static final String LIVE_URL = "https://api.alpaca.markets";

    public boolean configured() {
        return keyId != null && !keyId.isBlank() && secretKey != null && !secretKey.isBlank();
    }

    public String tradingUrl() {
        return paper ? PAPER_URL : LIVE_URL;
    }
}
