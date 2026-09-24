package com.quant.market;

import com.quant.config.AlpacaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class MarketDataConfig {

    private static final Logger log = LoggerFactory.getLogger(MarketDataConfig.class);

    @Bean
    MarketDataService marketDataService(AlpacaProperties props, RestClient.Builder builder) {
        if (props.configured()) {
            return new AlpacaMarketDataService(props, builder);
        }
        log.warn("ALPACA_KEY_ID / ALPACA_SECRET_KEY not set: using SYNTHETIC demo market data, trading disabled.");
        return new DemoMarketDataService();
    }
}
