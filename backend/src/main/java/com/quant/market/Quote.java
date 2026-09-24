package com.quant.market;

import java.time.Instant;

public record Quote(String symbol, double price, double previousClose, double change, double changePercent,
                    Instant time) {

    public static Quote of(String symbol, double price, double previousClose, Instant time) {
        double change = price - previousClose;
        double pct = previousClose == 0 ? 0 : change / previousClose * 100;
        return new Quote(symbol, price, previousClose, change, pct, time);
    }
}
