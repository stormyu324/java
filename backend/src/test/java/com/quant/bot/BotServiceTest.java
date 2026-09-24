package com.quant.bot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.quant.TestBars;
import com.quant.market.MarketDataService;
import com.quant.strategy.StrategyType;
import com.quant.trading.BrokerClient;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.BrokerModels.Position;
import com.quant.trading.OrderOutcome;
import com.quant.trading.OrderRequest;
import com.quant.trading.TradingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class BotServiceTest {

    @Autowired
    BotService service;
    @Autowired
    StrategyBotRepository repo;
    @MockitoBean
    MarketDataService marketData;
    @MockitoBean
    BrokerClient broker;
    @MockitoBean
    TradingService trading;

    private static final Order ACCEPTED = new Order("id", null, "AAPL", null, null, null, null, "buy", "market",
            "day", null, "accepted", Instant.now(), null);

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private StrategyBot smaBot() {
        return service.create(new BotRequest("trend", "aapl", StrategyType.SMA_CROSS,
                Map.of("fast", 2.0, "slow", 3.0), new BigDecimal("1000"), true));
    }

    @Test
    void buysWholeSharesWhenSignalIsBuyAndFlat() {
        StrategyBot bot = smaBot();
        when(marketData.bars(eq("AAPL"), any(), any(), any())).thenReturn(TestBars.fromCloses(90, 95, 100, 110, 120));
        when(broker.position("AAPL")).thenReturn(Optional.empty());
        when(trading.place(any(), anyString())).thenReturn(OrderOutcome.submitted(ACCEPTED));

        BotRunResult r = service.run(bot.getId(), false);

        ArgumentCaptor<OrderRequest> req = ArgumentCaptor.forClass(OrderRequest.class);
        verify(trading).place(req.capture(), eq("bot:" + bot.getId()));
        assertThat(req.getValue().qty()).isEqualByComparingTo("8"); // floor(1000 / 120)
        assertThat(req.getValue().side()).isEqualTo(OrderRequest.Side.BUY);
        assertThat(r.action()).isEqualTo("BUY");
        assertThat(repo.findById(bot.getId()).orElseThrow().getLastAction()).isEqualTo("BUY");
    }

    @Test
    void closesPositionWhenSignalIsSell() {
        StrategyBot bot = smaBot();
        when(marketData.bars(eq("AAPL"), any(), any(), any())).thenReturn(TestBars.fromCloses(120, 110, 100, 95, 90));
        when(broker.position("AAPL")).thenReturn(Optional.of(
                new Position("AAPL", new BigDecimal("5"), "long", null, null, null, null, null, null, null)));

        when(trading.closePosition(anyString(), anyString())).thenReturn(OrderOutcome.submitted(ACCEPTED));

        assertThat(service.run(bot.getId(), false).action()).isEqualTo("SELL");
        verify(trading).closePosition("AAPL", "bot:" + bot.getId());
    }

    @Test
    void dryRunNeverTrades() {
        StrategyBot bot = smaBot();
        when(marketData.bars(eq("AAPL"), any(), any(), any())).thenReturn(TestBars.fromCloses(90, 95, 100, 110, 120));

        BotRunResult r = service.run(bot.getId(), true);

        assertThat(r.signal()).isEqualTo("BUY");
        assertThat(r.dryRun()).isTrue();
        verify(trading, never()).place(any(), anyString());
        verify(broker, never()).position(any());
    }

    @Test
    void onlyOneBotPerSymbol() {
        smaBot();
        assertThatThrownBy(() -> service.create(new BotRequest("dup", "AAPL", StrategyType.BREAKOUT, null,
                new BigDecimal("500"), false))).isInstanceOf(IllegalArgumentException.class);
    }
}
