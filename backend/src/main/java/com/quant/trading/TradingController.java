package com.quant.trading;

import com.quant.trading.BrokerModels.Account;
import com.quant.trading.BrokerModels.Clock;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.BrokerModels.Position;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trading")
public class TradingController {

    private final BrokerClient broker;
    private final TradingService trading;

    public TradingController(BrokerClient broker, TradingService trading) {
        this.broker = broker;
        this.trading = trading;
    }

    @GetMapping("/account")
    public Account account() {
        return broker.account();
    }

    @GetMapping("/clock")
    public Clock clock() {
        return broker.clock();
    }

    @GetMapping("/positions")
    public List<Position> positions() {
        return broker.positions();
    }

    @GetMapping("/orders")
    public List<Order> orders(@RequestParam(defaultValue = "all") String status,
                              @RequestParam(defaultValue = "50") int limit) {
        if (!List.of("open", "closed", "all").contains(status)) {
            throw new IllegalArgumentException("status must be open, closed or all");
        }
        return broker.orders(status, Math.min(Math.max(limit, 1), 500));
    }

    @PostMapping("/orders")
    public Order place(@Valid @RequestBody OrderRequest req) {
        return trading.place(req, "manual");
    }

    @DeleteMapping("/orders/{id}")
    public void cancel(@PathVariable String id) {
        if (!id.matches("[0-9a-fA-F-]{36}")) {
            throw new IllegalArgumentException("Invalid order id");
        }
        broker.cancel(id);
    }

    @DeleteMapping("/positions/{symbol}")
    public Order close(@PathVariable String symbol) {
        return trading.closePosition(symbol, "manual");
    }

    @GetMapping("/logs")
    public List<TradeLog> logs() {
        return trading.recentLogs();
    }
}
