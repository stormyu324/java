package com.quant.strategy;

import com.quant.indicator.Indicators;
import com.quant.market.Bar;
import java.util.Arrays;
import java.util.List;

/** Donchian channel breakout: buy on a close above the prior N-day high, exit on a close below the prior M-day low. */
public class BreakoutStrategy implements Strategy {

    private final int entry;
    private final int exit;

    public BreakoutStrategy(int entry, int exit) {
        this.entry = entry;
        this.exit = exit;
    }

    @Override
    public Signal[] signals(List<Bar> bars) {
        double[] c = Strategy.closes(bars);
        double[] hi = Indicators.priorHighest(bars.stream().mapToDouble(Bar::high).toArray(), entry);
        double[] lo = Indicators.priorLowest(bars.stream().mapToDouble(Bar::low).toArray(), exit);
        Signal[] out = new Signal[c.length];
        Arrays.fill(out, Signal.HOLD);
        for (int i = 0; i < c.length; i++) {
            if (!Double.isNaN(hi[i]) && c[i] > hi[i]) {
                out[i] = Signal.BUY;
            } else if (!Double.isNaN(lo[i]) && c[i] < lo[i]) {
                out[i] = Signal.SELL;
            }
        }
        return out;
    }

    @Override
    public int warmup() {
        return Math.max(entry, exit);
    }
}
