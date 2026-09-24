package com.quant.trading;

import com.quant.trading.BrokerModels.Order;

/** Result of asking to trade: either sent to the broker, or held for the owner's confirmation. */
public record OrderOutcome(Status status, Order order, PendingOrder pending) {

    public enum Status { SUBMITTED, PENDING_APPROVAL }

    public static OrderOutcome submitted(Order order) {
        return new OrderOutcome(Status.SUBMITTED, order, null);
    }

    public static OrderOutcome pending(PendingOrder pending) {
        return new OrderOutcome(Status.PENDING_APPROVAL, null, pending);
    }
}
