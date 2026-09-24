package com.quant.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.quant.TestBars;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategyTest {

    @Test
    void smaCrossGoesLongInUptrendAndFlatInDowntrend() {
        Signal[] s = new SmaCrossStrategy(2, 3).signals(TestBars.fromCloses(10, 11, 12, 13, 12, 10, 8));
        assertThat(s[0]).isEqualTo(Signal.HOLD);
        assertThat(s[2]).isEqualTo(Signal.BUY);
        assertThat(s[3]).isEqualTo(Signal.BUY);
        assertThat(s[6]).isEqualTo(Signal.SELL);
    }

    @Test
    void breakoutBuysAboveChannelAndSellsBelow() {
        Signal[] s = new BreakoutStrategy(3, 2).signals(TestBars.fromCloses(10, 10, 10, 12, 11, 8));
        assertThat(s[3]).isEqualTo(Signal.BUY);
        assertThat(s[4]).isEqualTo(Signal.HOLD);
        assertThat(s[5]).isEqualTo(Signal.SELL);
    }

    @Test
    void factoryAppliesDefaultsAndOverrides() {
        assertThat(StrategyType.SMA_CROSS.create(null).warmup()).isEqualTo(50);
        assertThat(StrategyType.SMA_CROSS.create(Map.of("slow", 30.0)).warmup()).isEqualTo(30);
    }

    @Test
    void factoryRejectsBadParameters() {
        assertThatThrownBy(() -> StrategyType.SMA_CROSS.create(Map.of("fast", 60.0, "slow", 50.0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StrategyType.BREAKOUT.create(Map.of("entry", 0.0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
