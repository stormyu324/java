package com.quant.indicator;

import java.util.Arrays;

/**
 * Technical indicators over a price array. Output arrays have the same length as the input; positions
 * without enough history are {@link Double#NaN}. Value {@code i} only uses inputs {@code 0..i}.
 */
public final class Indicators {

    private Indicators() {
    }

    public static double[] sma(double[] values, int period) {
        requirePeriod(period);
        double[] out = nan(values.length);
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) {
                sum -= values[i - period];
            }
            if (i >= period - 1) {
                out[i] = sum / period;
            }
        }
        return out;
    }

    public static double[] ema(double[] values, int period) {
        requirePeriod(period);
        double[] out = nan(values.length);
        if (values.length < period) {
            return out;
        }
        double k = 2.0 / (period + 1);
        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += values[i];
        }
        out[period - 1] = seed / period;
        for (int i = period; i < values.length; i++) {
            out[i] = values[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Wilder's RSI. */
    public static double[] rsi(double[] values, int period) {
        requirePeriod(period);
        double[] out = nan(values.length);
        if (values.length <= period) {
            return out;
        }
        double gain = 0;
        double loss = 0;
        for (int i = 1; i <= period; i++) {
            double d = values[i] - values[i - 1];
            gain += Math.max(d, 0);
            loss += Math.max(-d, 0);
        }
        gain /= period;
        loss /= period;
        out[period] = rsiValue(gain, loss);
        for (int i = period + 1; i < values.length; i++) {
            double d = values[i] - values[i - 1];
            gain = (gain * (period - 1) + Math.max(d, 0)) / period;
            loss = (loss * (period - 1) + Math.max(-d, 0)) / period;
            out[i] = rsiValue(gain, loss);
        }
        return out;
    }

    /** Highest value over the {@code period} values strictly before {@code i}. */
    public static double[] priorHighest(double[] values, int period) {
        requirePeriod(period);
        double[] out = nan(values.length);
        for (int i = period; i < values.length; i++) {
            double m = Double.NEGATIVE_INFINITY;
            for (int j = i - period; j < i; j++) {
                m = Math.max(m, values[j]);
            }
            out[i] = m;
        }
        return out;
    }

    /** Lowest value over the {@code period} values strictly before {@code i}. */
    public static double[] priorLowest(double[] values, int period) {
        requirePeriod(period);
        double[] out = nan(values.length);
        for (int i = period; i < values.length; i++) {
            double m = Double.POSITIVE_INFINITY;
            for (int j = i - period; j < i; j++) {
                m = Math.min(m, values[j]);
            }
            out[i] = m;
        }
        return out;
    }

    private static double rsiValue(double avgGain, double avgLoss) {
        if (avgLoss == 0) {
            return avgGain == 0 ? 50 : 100;
        }
        return 100 - 100 / (1 + avgGain / avgLoss);
    }

    private static double[] nan(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }

    private static void requirePeriod(int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
    }
}
