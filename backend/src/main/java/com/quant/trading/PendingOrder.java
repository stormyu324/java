package com.quant.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;

/** An order that passed risk checks but is held until the account owner confirms it. */
@Entity
public class PendingOrder {

    public enum Status { PENDING, APPROVED, REJECTED, EXPIRED, FAILED }

    /** BUY / SELL are normal orders; CLOSE sells the whole position. */
    public enum Action { BUY, SELL, CLOSE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant createdAt;
    private Instant expiresAt;
    private String source;
    /** Account mode the order was created for; it can only be confirmed while the app runs in the same mode. */
    private String mode;
    private String symbol;
    @Enumerated(EnumType.STRING)
    private Action action;
    @Column(precision = 19, scale = 6)
    private BigDecimal qty;
    @Enumerated(EnumType.STRING)
    private OrderRequest.Type type;
    @Column(precision = 19, scale = 6)
    private BigDecimal limitPrice;
    @Enumerated(EnumType.STRING)
    private OrderRequest.TimeInForce timeInForce;
    /** Price when the order was created, for display only. */
    @Column(precision = 19, scale = 6)
    private BigDecimal referencePrice;
    @Enumerated(EnumType.STRING)
    private Status status;
    private Instant decidedAt;
    private String brokerOrderId;
    @Column(length = 1000)
    private String message;

    protected PendingOrder() {
    }

    static PendingOrder order(String source, String mode, String symbol, OrderRequest req, BigDecimal referencePrice,
                              Instant expiresAt) {
        PendingOrder p = new PendingOrder(source, mode, symbol, expiresAt);
        p.action = req.side() == OrderRequest.Side.BUY ? Action.BUY : Action.SELL;
        p.qty = req.qty();
        p.type = req.type();
        p.limitPrice = req.limitPrice();
        p.timeInForce = req.timeInForce() == null ? OrderRequest.TimeInForce.DAY : req.timeInForce();
        p.referencePrice = referencePrice;
        return p;
    }

    static PendingOrder close(String source, String mode, String symbol, Instant expiresAt) {
        PendingOrder p = new PendingOrder(source, mode, symbol, expiresAt);
        p.action = Action.CLOSE;
        return p;
    }

    private PendingOrder(String source, String mode, String symbol, Instant expiresAt) {
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.source = source;
        this.mode = mode;
        this.symbol = symbol;
        this.status = Status.PENDING;
    }

    OrderRequest toRequest() {
        return new OrderRequest(symbol, action == Action.BUY ? OrderRequest.Side.BUY : OrderRequest.Side.SELL, qty,
                type, limitPrice, timeInForce);
    }

    boolean isActive(Instant now) {
        return status == Status.PENDING && now.isBefore(expiresAt);
    }

    /** Marks an overdue PENDING order as EXPIRED. Returns true if it changed. */
    boolean expireIfDue(Instant now) {
        if (status == Status.PENDING && !now.isBefore(expiresAt)) {
            decide(Status.EXPIRED, null, "Not confirmed in time");
            return true;
        }
        return false;
    }

    void decide(Status status, String brokerOrderId, String message) {
        this.status = status;
        this.decidedAt = Instant.now();
        this.brokerOrderId = brokerOrderId;
        this.message = message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getSource() { return source; }
    public String getMode() { return mode; }
    public String getSymbol() { return symbol; }
    public Action getAction() { return action; }
    public BigDecimal getQty() { return qty; }
    public OrderRequest.Type getType() { return type; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public OrderRequest.TimeInForce getTimeInForce() { return timeInForce; }
    public BigDecimal getReferencePrice() { return referencePrice; }
    public Status getStatus() { return status; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getBrokerOrderId() { return brokerOrderId; }
    public String getMessage() { return message; }
}
