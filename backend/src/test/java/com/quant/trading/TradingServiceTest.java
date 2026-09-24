package com.quant.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TradingServiceTest {

    private static final AlpacaProperties PAPER = new AlpacaProperties("k", "s", true, "", "iex");
    private static final AlpacaProperties LIVE = new AlpacaProperties("k", "s", false, "", "iex");
    private static final TradingProperties LIMITS = limits(true, false, false);
    private static final TradingProperties LIVE_ON = limits(true, true, false);
    private static final Instant NOW = Instant.parse("2026-03-02T15:00:00Z");

    @Autowired
    TradeLogRepository logs;
    @Autowired
    PendingOrderRepository pending;

    private BrokerClient broker;
    private MarketDataService marketData;

    private static TradingProperties limits(boolean enabled, boolean live, boolean approvalInPaper) {
        return new TradingProperties(enabled, live, new BigDecimal("5000"), 2, approvalInPaper, 30);
    }

    @BeforeEach
    void setUp() {
        pending.deleteAll();
        logs.deleteAll();
        broker = mock(BrokerClient.class);
        marketData = mock(MarketDataService.class);
        when(marketData.quotes(any())).thenReturn(Map.of("AAPL", Quote.of("AAPL", 200, 190, Instant.now())));
        when(broker.positions()).thenReturn(List.of());
        when(broker.submit(any())).thenReturn(new Order("id-1", null, "AAPL", BigDecimal.TEN, null, null, null,
                "buy", "market", "day", null, "accepted", Instant.now(), null));
    }

    private TradingService service(AlpacaProperties alpaca, TradingProperties limits) {
        return service(alpaca, limits, NOW);
    }

    private TradingService service(AlpacaProperties alpaca, TradingProperties limits, Instant now) {
        return new TradingService(broker, marketData, alpaca, limits, logs, pending, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static OrderRequest buy(String qty) {
        return new OrderRequest("aapl", OrderRequest.Side.BUY, new BigDecimal(qty), OrderRequest.Type.MARKET, null,
                null);
    }

    // ---- paper account: orders go straight to the broker ----

    @Test
    void paperSubmitsMarketOrderToBroker() {
        OrderOutcome out = service(PAPER, LIMITS).place(buy("10"), "manual");
        assertThat(out.status()).isEqualTo(OrderOutcome.Status.SUBMITTED);
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
        assertThat(logs.findAll()).extracting(TradeLog::getStatus).containsExactly("REJECTED");
    }

    @Test
    void refusesLiveAccountUnlessExplicitlyEnabled() {
        assertThatThrownBy(() -> service(LIVE, LIMITS).place(buy("1"), "manual"))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("TRADING_LIVE_ENABLED");
        assertThat(pending.findAll()).isEmpty();
        verify(broker, never()).submit(any());
    }

    @Test
    void killSwitchBlocksEverything() {
        TradingProperties off = limits(false, false, false);
        assertThatThrownBy(() -> service(PAPER, off).place(buy("1"), "manual"))
                .isInstanceOf(OrderRejectedException.class);
        assertThatThrownBy(() -> service(PAPER, off).closePosition("AAPL", "manual"))
                .isInstanceOf(OrderRejectedException.class);
        verify(broker, never()).submit(any());
        verify(broker, never()).closePosition(any());
    }

    @Test
    void enforcesMaxOpenPositionsForNewSymbolsOnly() {
        when(broker.positions()).thenReturn(List.of(position("MSFT"), position("NVDA")));
        assertThatThrownBy(() -> service(PAPER, LIMITS).place(buy("1"), "manual"))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("positions");

        when(broker.positions()).thenReturn(List.of(position("MSFT"), position("AAPL")));
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

    // ---- live account: nothing reaches the broker until the owner confirms ----

    @Test
    void liveOrdersAreHeldNotSubmitted() {
        TradingService svc = service(LIVE, LIVE_ON);
        assertThat(svc.approvalRequired()).isTrue();

        OrderOutcome manual = svc.place(buy("2"), "manual");
        OrderOutcome bot = svc.place(buy("1"), "bot:7");
        OrderOutcome close = svc.closePosition("MSFT", "bot:8");

        assertThat(List.of(manual, bot, close)).allMatch(o -> o.status() == OrderOutcome.Status.PENDING_APPROVAL);
        verify(broker, never()).submit(any());
        verify(broker, never()).closePosition(any());
        assertThat(svc.awaitingApproval()).hasSize(3);
        assertThat(manual.pending().getReferencePrice()).isEqualByComparingTo("200");
        assertThat(manual.pending().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
    }

    @Test
    void approvingSubmitsExactlyOnce() {
        TradingService svc = service(LIVE, LIVE_ON);
        long id = svc.place(buy("2"), "manual").pending().getId();

        OrderOutcome out = svc.approve(id);

        assertThat(out.status()).isEqualTo(OrderOutcome.Status.SUBMITTED);
        ArgumentCaptor<NewOrder> sent = ArgumentCaptor.forClass(NewOrder.class);
        verify(broker).submit(sent.capture());
        assertThat(sent.getValue().qty()).isEqualTo("2");
        assertThat(pending.findById(id).orElseThrow().getStatus()).isEqualTo(PendingOrder.Status.APPROVED);

        assertThatThrownBy(() -> svc.approve(id)).isInstanceOf(OrderRejectedException.class);
        verify(broker, times(1)).submit(any());
    }

    @Test
    void approvingCloseClosesPosition() {
        TradingService svc = service(LIVE, LIVE_ON);
        long id = svc.closePosition("aapl", "manual").pending().getId();
        svc.approve(id);
        verify(broker).closePosition("AAPL");
    }

    @Test
    void rejectedOrderCanNeverBeSubmitted() {
        TradingService svc = service(LIVE, LIVE_ON);
        long id = svc.place(buy("1"), "manual").pending().getId();
        svc.reject(id);
        assertThatThrownBy(() -> svc.approve(id)).isInstanceOf(OrderRejectedException.class);
        verify(broker, never()).submit(any());
        assertThat(pending.findById(id).orElseThrow().getStatus()).isEqualTo(PendingOrder.Status.REJECTED);
    }

    @Test
    void expiredOrderCannotBeConfirmed() {
        long id = service(LIVE, LIVE_ON).place(buy("1"), "manual").pending().getId();
        TradingService later = service(LIVE, LIVE_ON, NOW.plus(Duration.ofMinutes(31)));

        assertThatThrownBy(() -> later.approve(id)).isInstanceOf(OrderRejectedException.class)
                .hasMessageContaining("EXPIRED");
        assertThat(later.awaitingApproval()).isEmpty();
        verify(broker, never()).submit(any());
    }

    @Test
    void riskChecksRunAgainAtConfirmation() {
        long id = service(LIVE, LIVE_ON).place(buy("20"), "manual").pending().getId(); // $4000 when held
        when(marketData.quotes(any())).thenReturn(Map.of("AAPL", Quote.of("AAPL", 300, 290, Instant.now())));

        assertThatThrownBy(() -> service(LIVE, LIVE_ON).approve(id)) // now $6000 > $5000
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("per-order limit");
        assertThat(pending.findById(id).orElseThrow().getStatus()).isEqualTo(PendingOrder.Status.FAILED);
        verify(broker, never()).submit(any());
    }

    @Test
    void killSwitchAppliesAtConfirmation() {
        long id = service(LIVE, LIVE_ON).place(buy("1"), "manual").pending().getId();
        assertThatThrownBy(() -> service(LIVE, limits(false, true, false)).approve(id))
                .isInstanceOf(OrderRejectedException.class);
        verify(broker, never()).submit(any());
    }

    @Test
    void liveOrderCannotBeConfirmedAfterSwitchingToPaper() {
        long id = service(LIVE, LIVE_ON).place(buy("1"), "manual").pending().getId();
        assertThatThrownBy(() -> service(PAPER, LIMITS).approve(id))
                .isInstanceOf(OrderRejectedException.class).hasMessageContaining("live");
        verify(broker, never()).submit(any());
    }

    @Test
    void botDoesNotStackDuplicatePendingOrders() {
        TradingService svc = service(LIVE, LIVE_ON);
        long first = svc.place(buy("1"), "bot:3").pending().getId();
        long second = svc.place(buy("1"), "bot:3").pending().getId();
        assertThat(second).isEqualTo(first);
        assertThat(svc.awaitingApproval()).hasSize(1);
    }

    @Test
    void fractionalQuantityAndLimitPriceSurviveStorage() {
        TradingService svc = service(LIVE, LIVE_ON);
        OrderRequest req = new OrderRequest("AAPL", OrderRequest.Side.BUY, new BigDecimal("0.125"),
                OrderRequest.Type.LIMIT, new BigDecimal("199.5"), OrderRequest.TimeInForce.GTC);
        svc.approve(svc.place(req, "manual").pending().getId());
        ArgumentCaptor<NewOrder> sent = ArgumentCaptor.forClass(NewOrder.class);
        verify(broker).submit(sent.capture());
        assertThat(sent.getValue().qty()).isEqualTo("0.125");
        assertThat(sent.getValue().limitPrice()).isEqualTo("199.5");
        assertThat(sent.getValue().timeInForce()).isEqualTo("gtc");
    }

    @Test
    void paperCanOptIntoConfirmation() {
        OrderOutcome out = service(PAPER, limits(true, false, true)).place(buy("1"), "manual");
        assertThat(out.status()).isEqualTo(OrderOutcome.Status.PENDING_APPROVAL);
        verify(broker, never()).submit(any());
    }

    private static Position position(String symbol) {
        return new Position(symbol, BigDecimal.ONE, "long", null, null, null, null, null, null, null);
    }
}
