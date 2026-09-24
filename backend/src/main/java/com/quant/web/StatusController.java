package com.quant.web;

import com.quant.config.AlpacaProperties;
import com.quant.config.BotProperties;
import com.quant.config.TradingProperties;
import com.quant.market.MarketDataService;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final MarketDataService marketData;
    private final AlpacaProperties alpaca;
    private final TradingProperties trading;
    private final BotProperties bots;

    public StatusController(MarketDataService marketData, AlpacaProperties alpaca, TradingProperties trading,
                            BotProperties bots) {
        this.marketData = marketData;
        this.alpaca = alpaca;
        this.trading = trading;
        this.bots = bots;
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/status")
    public Status status() {
        return new Status(marketData.source(), alpaca.configured(), alpaca.paper() ? "paper" : "live",
                trading.enabled(), trading.liveEnabled(), !alpaca.paper() || trading.approvalInPaper(),
                trading.approvalTtlMinutes(), trading.maxOrderNotional(), trading.maxOpenPositions(),
                bots.schedulerEnabled(), bots.cron(), bots.zone());
    }

    public record Status(String dataSource, boolean brokerConfigured, String accountMode, boolean tradingEnabled,
                         boolean liveTradingEnabled, boolean approvalRequired, int approvalTtlMinutes,
                         BigDecimal maxOrderNotional, int maxOpenPositions,
                         boolean botSchedulerEnabled, String botCron, String botZone) {
    }
}
