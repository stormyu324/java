package com.quant.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.quant.TestBars;
import com.quant.market.Bar;
import com.quant.strategy.BuyAndHoldStrategy;
import com.quant.strategy.Signal;
import com.quant.strategy.Strategy;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestEngineTest {

    private final BacktestEngine engine = new BacktestEngine();

    /** Replays a fixed list of signals. */
    private static Strategy scripted(Signal... signals) {
        return new Strategy() {
            @Override
            public Signal[] signals(List<Bar> bars) {
                return signals;
            }

            @Override
            public int warmup() {
                return 0;
            }
        };
    }

    @Test
    void signalExecutesAtNextBarsOpenNotSameBarsClose() {
        // Signal BUY on bar 0 (close 100) must fill at bar 1's open (110), not 100.
        List<Bar> bars = TestBars.fromOpenClose(100, 100, 110, 120, 120, 130);
        var r = engine.run(bars, scripted(Signal.BUY, Signal.HOLD, Signal.HOLD), 1100, 0, 0);
        assertThat(r.trades()).hasSize(1);
        assertThat(r.trades().get(0).entryPrice()).isEqualTo(110.0);
        assertThat(r.trades().get(0).shares()).isEqualTo(10);
        assertThat(r.metrics().finalEquity()).isEqualTo(1300.0);
    }

    @Test
    void roundTripRecordsClosedTradeWithPnl() {
        List<Bar> bars = TestBars.fromOpenClose(100, 100, 100, 105, 110, 110, 110, 110);
        var r = engine.run(bars, scripted(Signal.BUY, Signal.SELL, Signal.HOLD, Signal.HOLD), 1000, 0, 0);
        assertThat(r.trades()).hasSize(1);
        var t = r.trades().get(0);
        assertThat(t.open()).isFalse();
        assertThat(t.exitPrice()).isEqualTo(110.0);
        assertThat(t.pnl()).isCloseTo(100.0, within(1e-9));
        assertThat(r.metrics().totalReturnPct()).isCloseTo(10.0, within(1e-9));
        assertThat(r.metrics().winRatePct()).isEqualTo(100.0);
    }

    @Test
    void commissionAndSlippageReduceReturns() {
        List<Bar> bars = TestBars.fromOpenClose(100, 100, 100, 100, 100, 100);
        var r = engine.run(bars, scripted(Signal.BUY, Signal.SELL, Signal.HOLD), 10_000, 10, 10);
        assertThat(r.metrics().finalEquity()).isLessThan(10_000);
        assertThat(r.trades().get(0).pnl()).isNegative();
    }

    @Test
    void maxDrawdownMeasuredFromPeak() {
        List<Bar> bars = TestBars.fromCloses(100, 100, 200, 100, 150);
        var r = engine.run(bars, new BuyAndHoldStrategy(), 100, 0, 0);
        assertThat(r.metrics().maxDrawdownPct()).isCloseTo(50.0, within(1e-9));
        assertThat(r.benchmark().totalReturnPct()).isCloseTo(50.0, within(1e-9));
    }
}
