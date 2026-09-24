package com.quant.trading;

import com.quant.trading.BrokerModels.Account;
import com.quant.trading.BrokerModels.Clock;
import com.quant.trading.BrokerModels.NewOrder;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.BrokerModels.Position;
import java.util.List;
import java.util.Optional;

public interface BrokerClient {

    Account account();

    List<Position> positions();

    Optional<Position> position(String symbol);

    List<Order> orders(String status, int limit);

    Order submit(NewOrder order);

    void cancel(String orderId);

    Order closePosition(String symbol);

    Clock clock();
}
