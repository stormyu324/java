package com.quant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bots")
public record BotProperties(boolean schedulerEnabled, String cron, String zone) {
}
