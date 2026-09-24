package com.quant.market;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface MarketDataService {

    /** Bars in ascending time order, split/dividend adjusted. */
    List<Bar> bars(String symbol, Timeframe timeframe, LocalDate from, LocalDate to);

    Map<String, Quote> quotes(List<String> symbols);

    /** "alpaca" or "demo" – shown in the UI so synthetic data is never mistaken for real prices. */
    String source();
}
