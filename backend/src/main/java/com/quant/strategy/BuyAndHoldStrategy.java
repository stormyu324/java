package com.quant.strategy;

import com.quant.market.Bar;
import java.util.Arrays;
import java.util.List;

public class BuyAndHoldStrategy implements Strategy {

    @Override
    public Signal[] signals(List<Bar> bars) {
        Signal[] out = new Signal[bars.size()];
        Arrays.fill(out, Signal.BUY);
        return out;
    }

    @Override
    public int warmup() {
        return 0;
    }
}
