package com.quant;

import com.quant.market.Bar;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public final class TestBars {

    private TestBars() {
    }

    /** Daily bars where open == close == the given price (high/low +-1%). */
    public static List<Bar> fromCloses(double... closes) {
        List<Bar> bars = new ArrayList<>();
        Instant t = Instant.parse("2024-01-02T05:00:00Z");
        for (double c : closes) {
            bars.add(new Bar(t, c, c * 1.01, c * 0.99, c, 1000));
            t = t.plus(1, ChronoUnit.DAYS);
        }
        return bars;
    }

    /** Bars with explicit open and close prices: pairs of (open, close). */
    public static List<Bar> fromOpenClose(double... openClose) {
        List<Bar> bars = new ArrayList<>();
        Instant t = Instant.parse("2024-01-02T05:00:00Z");
        for (int i = 0; i < openClose.length; i += 2) {
            double o = openClose[i];
            double c = openClose[i + 1];
            bars.add(new Bar(t, o, Math.max(o, c), Math.min(o, c), c, 1000));
            t = t.plus(1, ChronoUnit.DAYS);
        }
        return bars;
    }
}
