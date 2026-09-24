package com.quant.backtest;

import com.quant.backtest.BacktestResult.EquityPoint;
import com.quant.backtest.BacktestResult.Metrics;
import com.quant.backtest.BacktestResult.Trade;
import com.quant.market.Bar;
import com.quant.strategy.Signal;
import com.quant.strategy.Strategy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Long-only, single-symbol backtester.
 *
 * <p>The signal computed on bar {@code i}'s close is executed at bar {@code i+1}'s open (no look-ahead).
 * Entries invest all available cash in whole shares; exits sell the whole position. Commission and
 * slippage are charged on every fill.
 */
public class BacktestEngine {

    static final int TRADING_DAYS = 252;

    public Result run(List<Bar> bars, Strategy strategy, double initialCapital, double commissionBps,
                      double slippageBps) {
        if (bars.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 bars to backtest");
        }
        Signal[] signals = strategy.signals(bars);
        double commission = commissionBps / 10_000;
        double slippage = slippageBps / 10_000;

        double cash = initialCapital;
        long shares = 0;
        double entryCost = 0;
        Bar entryBar = null;
        double entryPrice = 0;
        int barsInMarket = 0;

        List<Trade> trades = new ArrayList<>();
        double[] equity = new double[bars.size()];
        double benchShares = initialCapital / bars.get(0).open();
        List<EquityPoint> curve = new ArrayList<>(bars.size());

        for (int i = 0; i < bars.size(); i++) {
            Bar bar = bars.get(i);
            Signal pending = i > 0 ? signals[i - 1] : Signal.HOLD;

            if (pending == Signal.BUY && shares == 0) {
                double fill = bar.open() * (1 + slippage);
                long qty = (long) Math.floor(cash / (fill * (1 + commission)));
                if (qty > 0) {
                    double cost = qty * fill;
                    double fee = cost * commission;
                    cash -= cost + fee;
                    shares = qty;
                    entryCost = cost + fee;
                    entryBar = bar;
                    entryPrice = fill;
                }
            } else if (pending == Signal.SELL && shares > 0) {
                double fill = bar.open() * (1 - slippage);
                double proceeds = shares * fill;
                double fee = proceeds * commission;
                cash += proceeds - fee;
                double pnl = proceeds - fee - entryCost;
                trades.add(new Trade(entryBar.time(), entryPrice, bar.time(), fill, shares, pnl,
                        pnl / entryCost * 100, false));
                shares = 0;
            }

            if (shares > 0) {
                barsInMarket++;
            }
            equity[i] = cash + shares * bar.close();
            curve.add(new EquityPoint(bar.time(), equity[i], benchShares * bar.close()));
        }

        if (shares > 0) {
            Bar last = bars.get(bars.size() - 1);
            double pnl = shares * last.close() - entryCost;
            trades.add(new Trade(entryBar.time(), entryPrice, null, null, shares, pnl, pnl / entryCost * 100, true));
        }

        double[] bench = curve.stream().mapToDouble(EquityPoint::benchmark).toArray();
        Duration span = Duration.between(bars.get(0).time(), bars.get(bars.size() - 1).time());
        Metrics metrics = metrics(equity, initialCapital, span, trades, (double) barsInMarket / bars.size() * 100);
        Metrics benchmark = metrics(bench, initialCapital, span, List.of(), 100);
        return new Result(metrics, benchmark, curve, trades);
    }

    static Metrics metrics(double[] equity, double initial, Duration span, List<Trade> trades, double exposurePct) {
        double last = equity[equity.length - 1];
        double totalReturn = last / initial - 1;
        double years = span.toDays() / 365.25;
        double cagr = years > 0 && last > 0 ? Math.pow(last / initial, 1 / years) - 1 : 0;

        double peak = initial;
        double maxDd = 0;
        for (double e : equity) {
            peak = Math.max(peak, e);
            maxDd = Math.max(maxDd, (peak - e) / peak);
        }

        int n = equity.length - 1;
        double mean = 0;
        double[] rets = new double[Math.max(n, 0)];
        for (int i = 1; i < equity.length; i++) {
            rets[i - 1] = equity[i - 1] == 0 ? 0 : equity[i] / equity[i - 1] - 1;
            mean += rets[i - 1];
        }
        mean = n > 0 ? mean / n : 0;
        double var = 0;
        for (double r : rets) {
            var += (r - mean) * (r - mean);
        }
        double sd = n > 1 ? Math.sqrt(var / (n - 1)) : 0;
        double sharpe = sd > 0 ? mean / sd * Math.sqrt(TRADING_DAYS) : 0;

        List<Trade> closed = trades.stream().filter(t -> !t.open()).toList();
        long wins = closed.stream().filter(t -> t.pnl() > 0).count();
        double winRate = closed.isEmpty() ? 0 : (double) wins / closed.size() * 100;

        return new Metrics(last, totalReturn * 100, cagr * 100, maxDd * 100, sharpe,
                sd * Math.sqrt(TRADING_DAYS) * 100, trades.size(), winRate, exposurePct);
    }

    public record Result(Metrics metrics, Metrics benchmark, List<EquityPoint> equity, List<Trade> trades) {
    }
}
