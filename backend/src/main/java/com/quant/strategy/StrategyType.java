package com.quant.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Catalogue of available strategies with their parameters and defaults (also drives the UI forms). */
public enum StrategyType {

    SMA_CROSS("均线交叉", "快线在慢线之上时持有，否则空仓",
            params("fast", 20.0, "slow", 50.0)),
    RSI_MEAN_REVERSION("RSI 均值回归", "RSI 低于下限买入（超卖），高于上限卖出（超买）",
            params("period", 14.0, "lower", 30.0, "upper", 70.0)),
    BREAKOUT("通道突破", "收盘价突破前 N 日最高价买入，跌破前 M 日最低价卖出",
            params("entry", 20.0, "exit", 10.0)),
    BUY_AND_HOLD("买入持有", "第一天买入一直持有，用作基准", params());

    private final String label;
    private final String description;
    private final Map<String, Double> defaults;

    StrategyType(String label, String description, Map<String, Double> defaults) {
        this.label = label;
        this.description = description;
        this.defaults = defaults;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Map<String, Double> defaults() {
        return defaults;
    }

    public Strategy create(Map<String, Double> params) {
        Map<String, Double> p = new LinkedHashMap<>(defaults);
        if (params != null) {
            params.forEach((k, v) -> {
                if (defaults.containsKey(k) && v != null) {
                    p.put(k, v);
                }
            });
        }
        return switch (this) {
            case SMA_CROSS -> new SmaCrossStrategy(period(p, "fast"), period(p, "slow"));
            case RSI_MEAN_REVERSION -> new RsiMeanReversionStrategy(period(p, "period"), p.get("lower"), p.get("upper"));
            case BREAKOUT -> new BreakoutStrategy(period(p, "entry"), period(p, "exit"));
            case BUY_AND_HOLD -> new BuyAndHoldStrategy();
        };
    }

    /** Insertion-ordered so the UI shows parameters in a sensible order. */
    private static Map<String, Double> params(Object... kv) {
        Map<String, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Double) kv[i + 1]);
        }
        return Collections.unmodifiableMap(m);
    }

    private static int period(Map<String, Double> p, String key) {
        int v = (int) Math.round(p.get(key));
        if (v < 1 || v > 500) {
            throw new IllegalArgumentException(key + " must be between 1 and 500");
        }
        return v;
    }
}
