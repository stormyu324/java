package com.quant.strategy;

import com.quant.indicator.Indicators;
import com.quant.market.Bar;
import java.util.Arrays;
import java.util.List;

/** Buy when RSI drops below {@code lower} (oversold), sell when it rises above {@code upper}. */
public class RsiMeanReversionStrategy implements Strategy {

    private final int period;
    private final double lower;
    private final double upper;

    public RsiMeanReversionStrategy(int period, double lower, double upper) {
        if (!(lower < upper)) {
            throw new IllegalArgumentException("lower must be below upper");
        }
        this.period = period;
        this.lower = lower;
        this.upper = upper;
    }

    @Override
    public Signal[] signals(List<Bar> bars) {
        double[] r = Indicators.rsi(Strategy.closes(bars), period);
        Signal[] out = new Signal[r.length];
        Arrays.fill(out, Signal.HOLD);
        for (int i = 0; i < r.length; i++) {
            if (r[i] < lower) {
                out[i] = Signal.BUY;
            } else if (r[i] > upper) {
                out[i] = Signal.SELL;
            }
        }
        return out;
    }

    @Override
    public int warmup() {
        return period + 1;
    }
}
