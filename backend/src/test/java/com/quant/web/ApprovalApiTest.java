package com.quant.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quant.market.MarketDataService;
import com.quant.market.Quote;
import com.quant.trading.BrokerClient;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.PendingOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** End to end over HTTP against a live-configured app: every order needs the password to go out. */
@SpringBootTest(properties = {
        "alpaca.key-id=k", "alpaca.secret-key=s", "alpaca.paper=false", "trading.live-enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApprovalApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PendingOrderRepository pending;
    @MockitoBean
    BrokerClient broker;
    @MockitoBean
    MarketDataService marketData;

    @BeforeEach
    void setUp() {
        pending.deleteAll();
        when(marketData.quotes(any())).thenReturn(Map.of("AAPL", Quote.of("AAPL", 200, 190, Instant.now())));
        when(broker.positions()).thenReturn(List.of());
        when(broker.submit(any())).thenReturn(new Order("id-1", null, "AAPL", BigDecimal.ONE, null, null, null,
                "buy", "market", "day", null, "accepted", Instant.now(), null));
    }

    private static MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder b) {
        return b.with(httpBasic("tester", "secret")).header("X-Requested-With", "quant-ui");
    }

    private long placeOrder() throws Exception {
        String body = mvc.perform(authed(post("/api/trading/orders")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\":\"AAPL\",\"side\":\"BUY\",\"qty\":1,\"type\":\"MARKET\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.path("pending").path("id").asLong();
    }

    @Test
    void statusSaysApprovalIsRequired() throws Exception {
        mvc.perform(get("/api/status").with(httpBasic("tester", "secret")))
                .andExpect(jsonPath("$.accountMode").value("live"))
                .andExpect(jsonPath("$.approvalRequired").value(true));
    }

    @Test
    void wrongPasswordDoesNotConfirm() throws Exception {
        long id = placeOrder();
        verify(broker, never()).submit(any());

        mvc.perform(authed(post("/api/trading/approvals/" + id + "/approve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"guess\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(authed(post("/api/trading/approvals/" + id + "/approve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verify(broker, never()).submit(any());
        mvc.perform(get("/api/trading/approvals").with(httpBasic("tester", "secret")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void correctPasswordSubmits() throws Exception {
        long id = placeOrder();
        mvc.perform(authed(post("/api/trading/approvals/" + id + "/approve"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.order.id").value("id-1"));
        verify(broker).submit(any());
    }

    @Test
    void closePositionIsAlsoHeld() throws Exception {
        mvc.perform(authed(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/trading/positions/AAPL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.pending.action").value("CLOSE"));
        verify(broker, never()).closePosition(any());
    }
}
