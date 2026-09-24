package com.quant.trading;

/** Raised when a risk check blocks an order before it reaches the broker. */
public class OrderRejectedException extends RuntimeException {

    public OrderRejectedException(String message) {
        super(message);
    }
}
