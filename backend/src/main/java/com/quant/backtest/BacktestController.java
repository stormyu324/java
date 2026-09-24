package com.quant.backtest;

import com.quant.market.Bar;
import com.quant.market.MarketDataService;
import com.quant.market.Symbols;
import com.quant.market.Timeframe;
import com.quant.strategy.Strategy;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BacktestController {

    private final MarketDataService marketData;
    private final BacktestEngine engine = new BacktestEngine();

    public BacktestController(MarketDataService marketData) {
        this.marketData = marketData;
    }

    @PostMapping("/api/backtest")
    public BacktestResult run(@Valid @RequestBody BacktestRequest req) {
        if (!req.from().isBefore(req.to())) {
            throw new IllegalArgumentException("from must be before to");
        }
        String symbol = Symbols.normalize(req.symbol());
        Strategy strategy = req.strategy().create(req.params());
        List<Bar> bars = marketData.bars(symbol, Timeframe.DAY_1, req.from(), req.to());
        if (bars.size() < strategy.warmup() + 2) {
            throw new IllegalArgumentException("Not enough data: got " + bars.size() + " bars, strategy needs at least "
                    + (strategy.warmup() + 2));
        }
        BacktestEngine.Result r = engine.run(bars, strategy, req.initialCapital(), req.commissionBps(),
                req.slippageBps());
        return new BacktestResult(symbol, marketData.source(), r.metrics(), r.benchmark(), r.equity(), r.trades());
    }
}
