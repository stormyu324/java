package com.quant.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class IndicatorsTest {

    @Test
    void smaAveragesTrailingWindow() {
        double[] sma = Indicators.sma(new double[] {1, 2, 3, 4, 5}, 3);
        assertThat(sma[0]).isNaN();
        assertThat(sma[1]).isNaN();
        assertThat(sma[2]).isEqualTo(2.0);
        assertThat(sma[3]).isEqualTo(3.0);
        assertThat(sma[4]).isEqualTo(4.0);
    }

    @Test
    void emaSeedsWithSmaThenSmooths() {
        double[] ema = Indicators.ema(new double[] {2, 4, 6, 8}, 3);
        assertThat(ema[1]).isNaN();
        assertThat(ema[2]).isEqualTo(4.0);
        assertThat(ema[3]).isCloseTo(8 * 0.5 + 4 * 0.5, within(1e-9));
    }

    @Test
    void rsiIsHundredWhenPricesOnlyRise() {
        double[] rsi = Indicators.rsi(new double[] {1, 2, 3, 4, 5, 6}, 3);
        assertThat(rsi[2]).isNaN();
        assertThat(rsi[3]).isEqualTo(100.0);
        assertThat(rsi[5]).isEqualTo(100.0);
    }

    @Test
    void rsiMatchesHandComputedValue() {
        // changes: +2, -1, +1 => avgGain = 1, avgLoss = 1/3 => RS = 3 => RSI = 75
        double[] rsi = Indicators.rsi(new double[] {10, 12, 11, 12}, 3);
        assertThat(rsi[3]).isCloseTo(75.0, within(1e-9));
    }

    @Test
    void priorHighestExcludesCurrentValue() {
        double[] hi = Indicators.priorHighest(new double[] {1, 5, 3, 9}, 2);
        assertThat(hi[1]).isNaN();
        assertThat(hi[2]).isEqualTo(5.0);
        assertThat(hi[3]).isEqualTo(5.0);
    }
}
