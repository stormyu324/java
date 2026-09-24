package com.quant.market;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Synthetic prices used when no Alpaca keys are configured, so the UI and backtester can be tried offline.
 * Each symbol gets a deterministic geometric random walk. These are NOT real prices.
 */
public class DemoMarketDataService implements MarketDataService {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final LocalDate ORIGIN = LocalDate.of(2015, 1, 2);

    @Override
    public List<Bar> bars(String symbol, Timeframe timeframe, LocalDate from, LocalDate to) {
        List<Bar> daily = dailySeries(symbol, to);
        List<Bar> out = new ArrayList<>();
        for (Bar b : daily) {
            LocalDate d = b.time().atZone(NEW_YORK).toLocalDate();
            if (!d.isBefore(from) && !d.isAfter(to)) {
                out.add(b);
            }
        }
        return timeframe == Timeframe.WEEK_1 ? toWeekly(out) : out;
    }

    @Override
    public Map<String, Quote> quotes(List<String> symbols) {
        Map<String, Quote> out = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(NEW_YORK);
        for (String s : symbols) {
            List<Bar> series = dailySeries(s, today);
            Bar last = series.get(series.size() - 1);
            Bar prev = series.get(series.size() - 2);
            out.put(s, Quote.of(s, last.close(), prev.close(), last.time()));
        }
        return out;
    }

    @Override
    public String source() {
        return "demo";
    }

    private List<Bar> dailySeries(String symbol, LocalDate to) {
        Random rnd = new Random(symbol.hashCode() * 31L + 7);
        double price = 20 + rnd.nextDouble() * 280;
        double drift = 0.0002 + rnd.nextDouble() * 0.0006;
        double vol = 0.012 + rnd.nextDouble() * 0.018;
        List<Bar> bars = new ArrayList<>();
        for (LocalDate d = ORIGIN; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            double open = price * (1 + rnd.nextGaussian() * vol * 0.3);
            double close = open * Math.exp(drift - vol * vol / 2 + rnd.nextGaussian() * vol);
            double high = Math.max(open, close) * (1 + Math.abs(rnd.nextGaussian()) * vol * 0.5);
            double low = Math.min(open, close) * (1 - Math.abs(rnd.nextGaussian()) * vol * 0.5);
            long volume = (long) (1_000_000 + rnd.nextDouble() * 9_000_000);
            Instant t = ZonedDateTime.of(d.atTime(4, 0), NEW_YORK).toInstant();
            bars.add(new Bar(t, round(open), round(high), round(low), round(close), volume));
            price = close;
        }
        return bars;
    }

    private static List<Bar> toWeekly(List<Bar> daily) {
        List<Bar> out = new ArrayList<>();
        Bar acc = null;
        LocalDate accWeek = null;
        for (Bar b : daily) {
            LocalDate week = b.time().atZone(NEW_YORK).toLocalDate().with(DayOfWeek.MONDAY);
            if (week.equals(accWeek)) {
                acc = new Bar(acc.time(), acc.open(), Math.max(acc.high(), b.high()), Math.min(acc.low(), b.low()),
                        b.close(), acc.volume() + b.volume());
            } else {
                if (acc != null) {
                    out.add(acc);
                }
                acc = b;
                accWeek = week;
            }
        }
        if (acc != null) {
            out.add(acc);
        }
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
