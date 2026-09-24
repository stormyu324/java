package com.quant.backtest;

import java.time.Instant;
import java.util.List;

public record BacktestResult(
        String symbol,
        String dataSource,
        Metrics metrics,
        Metrics benchmark,
        List<EquityPoint> equity,
        List<Trade> trades) {

    public record EquityPoint(Instant time, double equity, double benchmark) {
    }

    public record Trade(Instant entryTime, double entryPrice, Instant exitTime, Double exitPrice, long shares,
                        Double pnl, Double returnPct, boolean open) {
    }

    /** Percentages are expressed in percent (12.5 = 12.5%). */
    public record Metrics(double finalEquity, double totalReturnPct, double cagrPct, double maxDrawdownPct,
                          double sharpe, double volatilityPct, int trades, double winRatePct, double exposurePct) {
    }
}
