package com.quant.bot;

import com.quant.market.Bar;
import com.quant.market.MarketDataService;
import com.quant.market.Symbols;
import com.quant.market.Timeframe;
import com.quant.strategy.Signal;
import com.quant.strategy.Strategy;
import com.quant.trading.BrokerClient;
import com.quant.trading.BrokerModels.Position;
import com.quant.trading.OrderOutcome;
import com.quant.trading.OrderRequest;
import com.quant.trading.TradingService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotService {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final StrategyBotRepository bots;
    private final MarketDataService marketData;
    private final BrokerClient broker;
    private final TradingService trading;

    public BotService(StrategyBotRepository bots, MarketDataService marketData, BrokerClient broker,
                      TradingService trading) {
        this.bots = bots;
        this.marketData = marketData;
        this.broker = broker;
        this.trading = trading;
    }

    public List<StrategyBot> list() {
        return bots.findAll();
    }

    @Transactional
    public StrategyBot create(BotRequest req) {
        return save(new StrategyBot(), req);
    }

    @Transactional
    public StrategyBot update(long id, BotRequest req) {
        return save(get(id), req);
    }

    @Transactional
    public void delete(long id) {
        bots.delete(get(id));
    }

    public StrategyBot get(long id) {
        return bots.findById(id).orElseThrow(() -> new NoSuchElementException("Bot " + id + " not found"));
    }

    /**
     * Evaluates the bot's strategy on the latest daily bars and, unless {@code dryRun}, moves the position
     * toward the signal: BUY with no position opens one, SELL with a position closes it.
     * Not transactional on purpose: the trade audit log must survive a failed order.
     */
    public BotRunResult run(long id, boolean dryRun) {
        StrategyBot bot = get(id);
        Strategy strategy = bot.getStrategy().create(bot.getParams());
        LocalDate today = LocalDate.now(NEW_YORK);
        int lookbackDays = Math.max(400, strategy.warmup() * 3);
        List<Bar> bars = marketData.bars(bot.getSymbol(), Timeframe.DAY_1, today.minusDays(lookbackDays), today);
        if (bars.size() < strategy.warmup() + 1) {
            return finish(bot, "HOLD", "NONE", "Not enough price history (" + bars.size() + " bars)", dryRun);
        }
        Signal signal = strategy.signals(bars)[bars.size() - 1];
        Bar last = bars.get(bars.size() - 1);

        if (dryRun) {
            return finish(bot, signal.name(), "NONE", "Dry run at close " + last.close(), true);
        }

        Optional<Position> position = broker.position(bot.getSymbol());
        boolean holding = position.map(p -> p.qty().signum() > 0).orElse(false);

        if (signal == Signal.BUY && !holding) {
            BigDecimal shares = bot.getNotional().divide(BigDecimal.valueOf(last.close()), 0, RoundingMode.DOWN);
            if (shares.signum() <= 0) {
                return finish(bot, signal.name(), "NONE",
                        "Notional $" + bot.getNotional() + " is below one share at $" + last.close(), false);
            }
            OrderOutcome o = trading.place(new OrderRequest(bot.getSymbol(), OrderRequest.Side.BUY, shares,
                    OrderRequest.Type.MARKET, null, OrderRequest.TimeInForce.DAY), "bot:" + bot.getId());
            if (o.status() == OrderOutcome.Status.PENDING_APPROVAL) {
                return finish(bot, signal.name(), "BUY_PENDING", "Buy " + shares
                        + " shares is waiting for your confirmation (#" + o.pending().getId() + ")", false);
            }
            return finish(bot, signal.name(), "BUY", "Bought " + shares + " shares, order " + o.order().status(), false);
        }
        if (signal == Signal.SELL && holding) {
            OrderOutcome o = trading.closePosition(bot.getSymbol(), "bot:" + bot.getId());
            if (o.status() == OrderOutcome.Status.PENDING_APPROVAL) {
                return finish(bot, signal.name(), "SELL_PENDING", "Selling " + position.get().qty()
                        + " shares is waiting for your confirmation (#" + o.pending().getId() + ")", false);
            }
            return finish(bot, signal.name(), "SELL", "Closed position of " + position.get().qty() + " shares"
                    + (o.order() == null ? "" : ", order " + o.order().status()), false);
        }
        return finish(bot, signal.name(), "NONE", holding ? "Holding position" : "Staying flat", false);
    }

    private BotRunResult finish(StrategyBot bot, String signal, String action, String message, boolean dryRun) {
        if (!dryRun) {
            bot.recordRun(signal, action, message);
            bots.save(bot);
        }
        return new BotRunResult(bot.getId(), bot.getSymbol(), signal, action, message, dryRun);
    }

    private StrategyBot save(StrategyBot bot, BotRequest req) {
        String symbol = Symbols.normalize(req.symbol());
        bots.findBySymbol(symbol).filter(other -> !other.getId().equals(bot.getId())).ifPresent(other -> {
            throw new IllegalArgumentException("Bot \"" + other.getName() + "\" already trades " + symbol
                    + "; only one bot per symbol is allowed so they don't fight over the position");
        });
        req.strategy().create(req.params()); // validates parameters
        Map<String, Double> params = new LinkedHashMap<>(req.strategy().defaults());
        if (req.params() != null) {
            req.params().forEach((k, v) -> {
                if (params.containsKey(k) && v != null) {
                    params.put(k, v);
                }
            });
        }
        bot.setName(req.name().trim());
        bot.setSymbol(symbol);
        bot.setStrategy(req.strategy());
        bot.setParams(params);
        bot.setNotional(req.notional());
        bot.setEnabled(req.enabled());
        return bots.save(bot);
    }
}
