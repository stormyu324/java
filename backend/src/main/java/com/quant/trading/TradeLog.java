package com.quant.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

/** Audit trail of every order attempt, including ones rejected by risk checks. */
@Entity
public class TradeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant time;
    /** "manual" or "bot:{id}". */
    private String source;
    /** "paper" or "live". */
    private String mode;
    private String symbol;
    private String side;
    @Column(precision = 19, scale = 6)
    private BigDecimal qty;
    private String status;
    private String brokerOrderId;
    @Column(length = 1000)
    private String message;

    protected TradeLog() {
    }

    public TradeLog(String source, String mode, String symbol, String side, BigDecimal qty) {
        this.time = Instant.now();
        this.source = source;
        this.mode = mode;
        this.symbol = symbol;
        this.side = side;
        this.qty = qty;
    }

    public TradeLog outcome(String status, String brokerOrderId, String message) {
        this.status = status;
        this.brokerOrderId = brokerOrderId;
        this.message = message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
        return this;
    }

    public Long getId() { return id; }
    public Instant getTime() { return time; }
    public String getSource() { return source; }
    public String getMode() { return mode; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public BigDecimal getQty() { return qty; }
    public String getStatus() { return status; }
    public String getBrokerOrderId() { return brokerOrderId; }
    public String getMessage() { return message; }
}
