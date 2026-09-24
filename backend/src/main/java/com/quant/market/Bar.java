package com.quant.market;

import java.time.Instant;

/** One OHLCV candle. */
public record Bar(Instant time, double open, double high, double low, double close, long volume) {
}
