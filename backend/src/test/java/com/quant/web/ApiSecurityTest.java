package com.quant.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiSecurityTest {

    private static final String BACKTEST = """
            {"symbol":"aapl","strategy":"SMA_CROSS","params":{"fast":10,"slow":30},
             "from":"2022-01-01","to":"2024-01-01","initialCapital":10000,"commissionBps":1,"slippageBps":2}
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/status").with(httpBasic("tester", "wrong"))).andExpect(status().isUnauthorized());
    }

    @Test
    void statusReportsDemoModeWithoutKeys() throws Exception {
        mvc.perform(get("/api/status").with(httpBasic("tester", "secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataSource").value("demo"))
                .andExpect(jsonPath("$.brokerConfigured").value(false))
                .andExpect(jsonPath("$.accountMode").value("paper"))
                .andExpect(jsonPath("$.liveTradingEnabled").value(false));
    }

    @Test
    void stateChangingRequestsNeedCsrfHeader() throws Exception {
        mvc.perform(post("/api/backtest").with(httpBasic("tester", "secret"))
                        .contentType(MediaType.APPLICATION_JSON).content(BACKTEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void backtestRunsOnDemoData() throws Exception {
        mvc.perform(post("/api/backtest").with(httpBasic("tester", "secret"))
                        .header("X-Requested-With", "quant-ui")
                        .contentType(MediaType.APPLICATION_JSON).content(BACKTEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.dataSource").value("demo"))
                .andExpect(jsonPath("$.equity.length()").value(org.hamcrest.Matchers.greaterThan(400)))
                .andExpect(jsonPath("$.metrics.sharpe").isNumber());
    }

    @Test
    void invalidSymbolIsBadRequest() throws Exception {
        mvc.perform(get("/api/market/bars/not-a-symbol!").with(httpBasic("tester", "secret")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tradingWithoutKeysIsServiceUnavailable() throws Exception {
        mvc.perform(get("/api/trading/account").with(httpBasic("tester", "secret")))
                .andExpect(status().isServiceUnavailable());
    }
}
