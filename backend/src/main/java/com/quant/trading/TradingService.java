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
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The only path orders take to the broker. Applies risk checks and records every attempt.
 *
 * <p>On a live account (and on paper when {@code trading.approval-in-paper} is set) orders are not sent
 * right away: they become {@link PendingOrder}s that only {@link #approve} sends, after the owner confirms.
 */
@Service
public class TradingService {

    private static final Logger log = LoggerFactory.getLogger(TradingService.class);

    private final BrokerClient broker;
    private final MarketDataService marketData;
    private final AlpacaProperties alpaca;
    private final TradingProperties limits;
    private final TradeLogRepository logs;
    private final PendingOrderRepository pending;
    private final Clock clock;

    @Autowired
    public TradingService(BrokerClient broker, MarketDataService marketData, AlpacaProperties alpaca,
                          TradingProperties limits, TradeLogRepository logs, PendingOrderRepository pending) {
        this(broker, marketData, alpaca, limits, logs, pending, Clock.systemUTC());
    }

    TradingService(BrokerClient broker, MarketDataService marketData, AlpacaProperties alpaca,
                   TradingProperties limits, TradeLogRepository logs, PendingOrderRepository pending, Clock clock) {
        this.broker = broker;
        this.marketData = marketData;
        this.alpaca = alpaca;
        this.limits = limits;
        this.logs = logs;
        this.pending = pending;
        this.clock = clock;
    }

    public String mode() {
        return alpaca.paper() ? "paper" : "live";
    }

    /** Live accounts always need the owner's confirmation; paper only when configured. */
    public boolean approvalRequired() {
        return !alpaca.paper() || limits.approvalInPaper();
    }

    public OrderOutcome place(OrderRequest req, String source) {
        String symbol = Symbols.normalize(req.symbol());
        TradeLog entry = new TradeLog(source, mode(), symbol, req.side().name(), req.qty());
        try {
            BigDecimal price = checkRisk(symbol, req);
            if (approvalRequired()) {
                PendingOrder p = hold(source, symbol, () -> PendingOrder.order(source, mode(), symbol, req, price,
                        expiry()));
                logs.save(entry.outcome("PENDING_APPROVAL", null, "Waiting for confirmation #" + p.getId()));
                return OrderOutcome.pending(p);
            }
            Order placed = submit(symbol, req);
            logs.save(entry.outcome("SUBMITTED", placed.id(), placed.status()));
            log.info("[{}] {} {} {} x{} -> {}", mode(), source, req.side(), symbol, req.qty(), placed.status());
            return OrderOutcome.submitted(placed);
        } catch (OrderRejectedException e) {
            logs.save(entry.outcome("REJECTED", null, e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            logs.save(entry.outcome("ERROR", null, e.getMessage()));
            throw e;
        }
    }

    /** Sells the entire position in {@code symbol}. */
    public OrderOutcome closePosition(String rawSymbol, String source) {
        String symbol = Symbols.normalize(rawSymbol);
        TradeLog entry = new TradeLog(source, mode(), symbol, "CLOSE", null);
        try {
            checkGlobalSwitches();
            if (approvalRequired()) {
                PendingOrder p = hold(source, symbol, () -> PendingOrder.close(source, mode(), symbol, expiry()));
                logs.save(entry.outcome("PENDING_APPROVAL", null, "Waiting for confirmation #" + p.getId()));
                return OrderOutcome.pending(p);
            }
            Order o = broker.closePosition(symbol);
            logs.save(entry.outcome("SUBMITTED", o == null ? null : o.id(), o == null ? null : o.status()));
            return OrderOutcome.submitted(o);
        } catch (OrderRejectedException e) {
            logs.save(entry.outcome("REJECTED", null, e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            logs.save(entry.outcome("ERROR", null, e.getMessage()));
            throw e;
        }
    }

    /**
     * Sends a held order to the broker. The caller must have verified the owner's password. Risk checks run
     * again because prices, positions and switches may have changed since the order was held.
     * Synchronized so a double click cannot submit the same order twice.
     */
    public synchronized OrderOutcome approve(long id) {
        PendingOrder p = pendingOrder(id);
        if (p.getStatus() != PendingOrder.Status.PENDING) {
            throw new OrderRejectedException("Order #" + id + " is " + p.getStatus() + " and can no longer be confirmed");
        }
        String side = p.getAction().name();
        TradeLog entry = new TradeLog(p.getSource() + " (confirmed)", mode(), p.getSymbol(), side, p.getQty());
        try {
            if (!p.getMode().equals(mode())) {
                throw new OrderRejectedException("Order was created for the " + p.getMode()
                        + " account but the app is now connected to " + mode());
            }
            Order o;
            if (p.getAction() == PendingOrder.Action.CLOSE) {
                checkGlobalSwitches();
                o = broker.closePosition(p.getSymbol());
            } else {
                checkRisk(p.getSymbol(), p.toRequest());
                o = submit(p.getSymbol(), p.toRequest());
            }
            String brokerId = o == null ? null : o.id();
            String status = o == null ? null : o.status();
            p.decide(PendingOrder.Status.APPROVED, brokerId, status);
            pending.save(p);
            logs.save(entry.outcome("SUBMITTED", brokerId, status));
            log.info("[{}] confirmed #{} {} {} x{} -> {}", mode(), id, side, p.getSymbol(), p.getQty(), status);
            return OrderOutcome.submitted(o);
        } catch (OrderRejectedException e) {
            p.decide(PendingOrder.Status.FAILED, null, e.getMessage());
            pending.save(p);
            logs.save(entry.outcome("REJECTED", null, e.getMessage()));
            throw e;
        } catch (RuntimeException e) {
            p.decide(PendingOrder.Status.FAILED, null, e.getMessage());
            pending.save(p);
            logs.save(entry.outcome("ERROR", null, e.getMessage()));
            throw e;
        }
    }

    public synchronized PendingOrder reject(long id) {
        PendingOrder p = pendingOrder(id);
        if (p.getStatus() != PendingOrder.Status.PENDING) {
            throw new OrderRejectedException("Order #" + id + " is " + p.getStatus() + " and can no longer be rejected");
        }
        p.decide(PendingOrder.Status.REJECTED, null, "Rejected by owner");
        logs.save(new TradeLog(p.getSource() + " (rejected)", p.getMode(), p.getSymbol(), p.getAction().name(),
                p.getQty()).outcome("REJECTED", null, "Rejected by owner"));
        return pending.save(p);
    }

    public List<PendingOrder> awaitingApproval() {
        expireOverdue();
        return pending.findByStatus(PendingOrder.Status.PENDING);
    }

    public List<PendingOrder> recentApprovals() {
        expireOverdue();
        return pending.findTop100ByOrderByCreatedAtDesc();
    }

    public List<TradeLog> recentLogs() {
        return logs.findTop100ByOrderByTimeDesc();
    }

    /** Loads a pending order, first expiring it if it is overdue. */
    private PendingOrder pendingOrder(long id) {
        PendingOrder p = pending.findById(id).orElseThrow(() -> new NoSuchElementException("Order #" + id + " not found"));
        if (p.expireIfDue(clock.instant())) {
            pending.save(p);
        }
        return p;
    }

    private void expireOverdue() {
        Instant now = clock.instant();
        for (PendingOrder p : pending.findByStatus(PendingOrder.Status.PENDING)) {
            if (p.expireIfDue(now)) {
                pending.save(p);
            }
        }
    }

    /**
     * Holds an order for confirmation. A bot that already has an unconfirmed order for the symbol gets that
     * one back instead of stacking duplicates on repeated runs.
     */
    private synchronized PendingOrder hold(String source, String symbol, java.util.function.Supplier<PendingOrder> create) {
        if (source.startsWith("bot:")) {
            Instant now = clock.instant();
            Optional<PendingOrder> existing = pending
                    .findBySourceAndSymbolAndStatus(source, symbol, PendingOrder.Status.PENDING).stream()
                    .filter(p -> p.isActive(now))
                    .findFirst();
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        PendingOrder p = pending.save(create.get());
        log.info("[{}] {} {} {} held for confirmation as #{}", mode(), source, p.getAction(), symbol, p.getId());
        return p;
    }

    private Instant expiry() {
        return clock.instant().plus(Duration.ofMinutes(Math.max(1, limits.approvalTtlMinutes())));
    }

    private Order submit(String symbol, OrderRequest req) {
        return broker.submit(new NewOrder(
                symbol,
                req.qty().stripTrailingZeros().toPlainString(),
                req.side().name().toLowerCase(),
                req.type().name().toLowerCase(),
                (req.timeInForce() == null ? OrderRequest.TimeInForce.DAY : req.timeInForce()).name().toLowerCase(),
                req.type() == OrderRequest.Type.LIMIT ? req.limitPrice().stripTrailingZeros().toPlainString() : null,
                "qt-" + UUID.randomUUID()));
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

    /** Returns the price used for the notional check. */
    private BigDecimal checkRisk(String symbol, OrderRequest req) {
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
            throw new OrderRejectedException("Order value ~$" + notional.setScale(2, RoundingMode.HALF_UP)
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
        return price;
    }
}
