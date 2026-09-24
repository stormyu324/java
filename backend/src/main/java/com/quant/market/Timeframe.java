package com.quant.market;

/** Bar sizes supported by the site, mapped to Alpaca's timeframe strings. */
public enum Timeframe {
    MIN_5("5Min"),
    MIN_15("15Min"),
    HOUR_1("1Hour"),
    DAY_1("1Day"),
    WEEK_1("1Week");

    private final String alpaca;

    Timeframe(String alpaca) {
        this.alpaca = alpaca;
    }

    public String alpaca() {
        return alpaca;
    }
}
