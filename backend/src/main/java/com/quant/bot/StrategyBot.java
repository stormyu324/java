package com.quant.bot;

import com.quant.strategy.StrategyType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** Runs one strategy on one symbol on daily bars and trades a fixed dollar amount. */
@Entity
public class StrategyBot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    @Column(unique = true)
    private String symbol;
    @Enumerated(EnumType.STRING)
    private StrategyType strategy;
    @Convert(converter = ParamsConverter.class)
    @Column(length = 1000)
    private Map<String, Double> params;
    /** Dollars to invest when the strategy enters (rounded down to whole shares). */
    private BigDecimal notional;
    private boolean enabled;

    private Instant lastRunAt;
    private String lastSignal;
    private String lastAction;
    @Column(length = 1000)
    private String lastMessage;

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public StrategyType getStrategy() { return strategy; }
    public void setStrategy(StrategyType strategy) { this.strategy = strategy; }
    public Map<String, Double> getParams() { return params; }
    public void setParams(Map<String, Double> params) { this.params = params; }
    public BigDecimal getNotional() { return notional; }
    public void setNotional(BigDecimal notional) { this.notional = notional; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getLastRunAt() { return lastRunAt; }
    public String getLastSignal() { return lastSignal; }
    public String getLastAction() { return lastAction; }
    public String getLastMessage() { return lastMessage; }

    public void recordRun(String signal, String action, String message) {
        this.lastRunAt = Instant.now();
        this.lastSignal = signal;
        this.lastAction = action;
        this.lastMessage = message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
