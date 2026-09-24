package com.quant.trading;

import com.quant.config.AlpacaProperties;
import com.quant.config.TradingProperties;
import com.quant.market.MarketDataService;
import com.quant.market.Quote;
import com.quant.market.Symbols;
import com.quant.trading.BrokerModels.NewOrder;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.BrokerModels.Position;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** The only path orders take to the broker. Applies risk checks and records every attempt. */
@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final BrokerClient broker;
    private final MarketDataService marketData;
    private final AlpacaProperties alpaca;
    private final TradingProperties limits;
    private final TradeLogRepository logs;

    public TradingService(BrokerClient broker, MarketDataService marketData, AlpacaProperties alpaca,
                          TradingProperties limits, TradeLogRepository logs) {
        this.broker = broker;
        this.marketData = marketData;
        this.alpaca = alpaca;
        this.limits = limits;
        this.logs = logs;
    }

    public String mode() {
        return alpaca.paper() ? "paper" : "live";
    }

    public Order place(OrderRequest req, String source) {
        String symbol = Symbols.normalize(req.symbol());
        TradeLog entry = new TradeLog(source, mode(), symbol, req.side().name(), req.qty());
        try {
            checkRisk(symbol, req);
            NewOrder order = new NewOrder(
                    symbol,
                    req.qty().toPlainString(),
                    req.side().name().toLowerCase(),
                    req.type().name().toLowerCase(),
                    (req.timeInForce() == null ? OrderRequest.TimeInForce.DAY : req.timeInForce()).name().toLowerCase(),
                    req.type() == OrderRequest.Type.LIMIT ? req.limitPrice().toPlainString() : null,
                    "qt-" + UUID.randomUUID());
            Order placed = broker.submit(order);
            logs.save(entry.outcome("SUBMITTED", placed.id(), placed.status()));
            log.info("[{}] {} {} {} x{} -> {}", mode(), source, req.side(), symbol, req.qty(), placed.status());
            return placed;
        } catch (OrderRejectedException e) {
            logs.save(entry.outcome("REJECTED", null, e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            logs.save(entry.outcome("ERROR", null, e.getMessage()));
            throw e;
        }
    }

    /** Sells the entire position in {@code symbol}. */
    public Order closePosition(String rawSymbol, String source) {
        String symbol = Symbols.normalize(rawSymbol);
        TradeLog entry = new TradeLog(source, mode(), symbol, "CLOSE", null);
        try {
            checkGlobalSwitches();
            Order o = broker.closePosition(symbol);
            logs.save(entry.outcome("SUBMITTED", o == null ? null : o.id(), o == null ? null : o.status()));
            return o;
        } catch (OrderRejectedException e) {
            logs.save(entry.outcome("REJECTED", null, e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            logs.save(entry.outcome("ERROR", null, e.getMessage()));
            throw e;
        }
    }

    public List<TradeLog> recentLogs() {
        return logs.findTop100ByOrderByTimeDesc();
    }

    private void checkGlobalSwitches() {
        if (!alpaca.configured()) {
            throw new BrokerNotConfiguredException();
        }
        if (!limits.enabled()) {
            throw new OrderRejectedException("Trading is disabled (TRADING_ENABLED=false)");
        }
        if (!alpaca.paper() && !limits.liveEnabled()) {
            throw new OrderRejectedException(
                    "Live account configured but live trading is not enabled (set TRADING_LIVE_ENABLED=true)");
        }
    }

    private void checkRisk(String symbol, OrderRequest req) {
        checkGlobalSwitches();
        if (req.type() == OrderRequest.Type.LIMIT && req.limitPrice() == null) {
            throw new OrderRejectedException("Limit orders need a limit price");
        }
        BigDecimal price = req.limitPrice();
        if (price == null) {
            Map<String, Quote> q = marketData.quotes(List.of(symbol));
            if (!q.containsKey(symbol)) {
                throw new OrderRejectedException("No price available for " + symbol);
            }
            price = BigDecimal.valueOf(q.get(symbol).price());
        }
        BigDecimal notional = price.multiply(req.qty());
        if (notional.compareTo(limits.maxOrderNotional()) > 0) {
            throw new OrderRejectedException("Order value ~$" + notional.setScale(2, java.math.RoundingMode.HALF_UP)
                    + " exceeds the per-order limit of $" + limits.maxOrderNotional());
        }
        if (req.side() == OrderRequest.Side.BUY) {
            List<Position> positions = broker.positions();
            boolean alreadyHeld = positions.stream().anyMatch(p -> p.symbol().equals(symbol));
            if (!alreadyHeld && positions.size() >= limits.maxOpenPositions()) {
                throw new OrderRejectedException("Already holding " + positions.size()
                        + " positions (limit " + limits.maxOpenPositions() + ")");
            }
        }
    }
}
