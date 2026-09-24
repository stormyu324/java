package com.quant.market;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataService marketData;

    public MarketController(MarketDataService marketData) {
        this.marketData = marketData;
    }

    @GetMapping("/bars/{symbol}")
    public BarsResponse bars(@PathVariable String symbol,
                             @RequestParam(defaultValue = "DAY_1") Timeframe timeframe,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusYears(1);
        String sym = Symbols.normalize(symbol);
        return new BarsResponse(sym, timeframe, marketData.source(), marketData.bars(sym, timeframe, start, end));
    }

    @GetMapping("/quotes")
    public Map<String, Quote> quotes(@RequestParam String symbols) {
        List<String> list = Arrays.stream(symbols.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Symbols::normalize).distinct().limit(50).toList();
        return marketData.quotes(list);
    }

    public record BarsResponse(String symbol, Timeframe timeframe, String source, List<Bar> bars) {
    }
}
