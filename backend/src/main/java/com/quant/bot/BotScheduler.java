package com.quant.bot;

import com.quant.config.AlpacaProperties;
import com.quant.config.BotProperties;
import com.quant.trading.BrokerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BotScheduler {

    private static final Logger log = LoggerFactory.getLogger(BotScheduler.class);

    private final BotProperties props;
    private final AlpacaProperties alpaca;
    private final BrokerClient broker;
    private final StrategyBotRepository bots;
    private final BotService service;

    public BotScheduler(BotProperties props, AlpacaProperties alpaca, BrokerClient broker,
                        StrategyBotRepository bots, BotService service) {
        this.props = props;
        this.alpaca = alpaca;
        this.broker = broker;
        this.bots = bots;
        this.service = service;
    }

    @Scheduled(cron = "${bots.cron}", zone = "${bots.zone}")
    public void runAll() {
        if (!props.schedulerEnabled() || !alpaca.configured()) {
            return;
        }
        try {
            if (!broker.clock().isOpen()) {
                log.info("Market closed (holiday?), skipping scheduled bot run");
                return;
            }
        } catch (RuntimeException e) {
            log.error("Could not read market clock, skipping bot run", e);
            return;
        }
        for (StrategyBot bot : bots.findByEnabledTrue()) {
            try {
                BotRunResult r = service.run(bot.getId(), false);
                log.info("Bot {} ({}): signal={} action={} {}", bot.getName(), r.symbol(), r.signal(), r.action(),
                        r.message());
            } catch (RuntimeException e) {
                log.error("Bot {} ({}) failed", bot.getName(), bot.getSymbol(), e);
                bot.recordRun("ERROR", "NONE", e.getMessage());
                bots.save(bot);
            }
        }
    }
}
