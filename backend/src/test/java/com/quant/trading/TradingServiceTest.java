package com.quant.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quant.config.AlpacaProperties;
import com.quant.config.TradingProperties;
import com.quant.market.MarketDataService;
import com.quant.market.Quote;
import com.quant.trading.BrokerModels.NewOrder;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.BrokerModels.Position;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TradingServiceTest {

    private static final AlpacaProperties PAPER = new AlpacaProperties("k", "s", true, "", "iex");
    private static final AlpacaProperties LIVE = new AlpacaProperties("k", "s", false, "", "iex");
    private static final TradingProperties LIMITS = new TradingProperties(true, false, new BigDecimal("5000"), 2);

    private BrokerClient broker;
    private MarketDataService marketData;
    private TradeLogRepository logs;

    @BeforeEach
    void setUp() {
        broker = mock(BrokerClient.class);
        marketData = mock(MarketDataService.class);
        logs = mock(TradeLogRepository.class);
        when(marketData.quotes(any())).thenReturn(Map.of("AAPL", Quote.of("AAPL", 200, 190, Instant.now())));
        when(broker.positions()).thenReturn(List.of());
        when(broker.submit(any())).thenReturn(new Order("id-1", null, "AAPL", BigDecimal.TEN, null, null, null,
                "buy", "market", "day", null, "accepted", Instant.now(), null));
    }

    private TradingService service(AlpacaProperties alpaca, TradingProperties limits) {
        return new TradingService(broker, marketData, alpaca, limits, logs);
    }

    private static OrderRequest buy(String qty) {
        return new OrderRequest("aapl", OrderRequest.Side.BUY, new BigDecimal(qty), OrderRequest.Type.MARKET, null,
                null);
    }

    @Test
    void submitsMarketOrderToBroker() {
        service(PAPER, LIMITS).place(buy("10"), "manual");
        ArgumentCaptor<NewOrder> sent = ArgumentCaptor.forClass(NewOrder.class);
        verify(broker).submit(sent.capture());
        assertThat(sent.getValue().symbol()).isEqualTo("AAPL");
        assertThat(sent.getValue().qty()).isEqualTo("10");
        assertThat(sent.getValue().side()).isEqualTo("buy");
        assertThat(sent.getValue().timeInForce()).isEqualTo("day");
        assertThat(sent.getValue().limitPrice()).isNull();
    }

    @Test
    void rejectsOrdersAboveNotionalLimit() {
        // 30 * $200 = $6000 > $5000 limit
        assertThatThrownBy(() -> service(PAPER, LIMITS).place(buy("30"), "manual"))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("per-order limit");
        verify(broker, never()).submit(any());
        verify(logs).save(any());
    }

    @Test
    void refusesLiveAccountUnlessExplicitlyEnabled() {
        assertThatThrownBy(() -> service(LIVE, LIMITS).place(buy("1"), "manual"))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("TRADING_LIVE_ENABLED");
        verify(broker, never()).submit(any());

        service(LIVE, new TradingProperties(true, true, new BigDecimal("5000"), 2)).place(buy("1"), "manual");
        verify(broker).submit(any());
    }

    @Test
    void killSwitchBlocksEverything() {
        TradingProperties off = new TradingProperties(false, false, new BigDecimal("5000"), 2);
        assertThatThrownBy(() -> service(PAPER, off).place(buy("1"), "manual"))
                .isInstanceOf(OrderRejectedException.class);
        assertThatThrownBy(() -> service(PAPER, off).closePosition("AAPL", "manual"))
                .isInstanceOf(OrderRejectedException.class);
        verify(broker, never()).submit(any());
        verify(broker, never()).closePosition(any());
    }

    @Test
    void enforcesMaxOpenPositionsForNewSymbolsOnly() {
        Position msft = position("MSFT");
        Position nvda = position("NVDA");
        when(broker.positions()).thenReturn(List.of(msft, nvda));
        assertThatThrownBy(() -> service(PAPER, LIMITS).place(buy("1"), "manual"))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("positions");

        when(broker.positions()).thenReturn(List.of(msft, position("AAPL")));
        service(PAPER, LIMITS).place(buy("1"), "manual");
        verify(broker).submit(any());
    }

    @Test
    void limitOrderRequiresPrice() {
        OrderRequest req = new OrderRequest("AAPL", OrderRequest.Side.BUY, BigDecimal.ONE, OrderRequest.Type.LIMIT,
                null, null);
        assertThatThrownBy(() -> service(PAPER, LIMITS).place(req, "manual"))
                .isInstanceOf(OrderRejectedException.class);
    }

    @Test
    void notConfiguredBrokerIsReported() {
        AlpacaProperties none = new AlpacaProperties("", "", true, "", "iex");
        assertThatThrownBy(() -> service(none, LIMITS).place(buy("1"), "manual"))
                .isInstanceOf(BrokerNotConfiguredException.class);
    }

    private static Position position(String symbol) {
        return new Position(symbol, BigDecimal.ONE, "long", null, null, null, null, null, null, null);
    }
}
