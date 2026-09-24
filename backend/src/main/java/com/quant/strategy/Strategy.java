package com.quant.strategy;

import com.quant.market.Bar;
import java.util.List;

/**
 * A long-only daily strategy. {@code signals[i]} may only use bars {@code 0..i}; the backtester and the bots
 * act on it at the next opportunity after bar {@code i} closes, so there is no look-ahead.
 */
public interface Strategy {

    Signal[] signals(List<Bar> bars);

    /** Bars needed before the first non-HOLD signal can appear. */
    int warmup();

    static double[] closes(List<Bar> bars) {
        return bars.stream().mapToDouble(Bar::close).toArray();
    }
}
