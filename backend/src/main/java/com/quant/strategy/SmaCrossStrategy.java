package com.quant.strategy;

import com.quant.indicator.Indicators;
import com.quant.market.Bar;
import java.util.Arrays;
import java.util.List;

/** Long while the fast SMA is above the slow SMA, flat otherwise. */
public class SmaCrossStrategy implements Strategy {

    private final int fast;
    private final int slow;

    public SmaCrossStrategy(int fast, int slow) {
        if (fast >= slow) {
            throw new IllegalArgumentException("fast period must be smaller than slow period");
        }
        this.fast = fast;
        this.slow = slow;
    }

    @Override
    public Signal[] signals(List<Bar> bars) {
        double[] c = Strategy.closes(bars);
        double[] f = Indicators.sma(c, fast);
        double[] s = Indicators.sma(c, slow);
        Signal[] out = new Signal[c.length];
        Arrays.fill(out, Signal.HOLD);
        for (int i = 0; i < c.length; i++) {
            if (!Double.isNaN(s[i])) {
                out[i] = f[i] > s[i] ? Signal.BUY : Signal.SELL;
            }
        }
        return out;
    }

    @Override
    public int warmup() {
        return slow;
    }
}
