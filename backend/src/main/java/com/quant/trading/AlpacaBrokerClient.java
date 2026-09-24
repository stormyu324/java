package com.quant.trading;

import com.quant.config.AlpacaProperties;
import com.quant.trading.BrokerModels.Account;
import com.quant.trading.BrokerModels.Clock;
import com.quant.trading.BrokerModels.NewOrder;
import com.quant.trading.BrokerModels.Order;
import com.quant.trading.BrokerModels.Position;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Alpaca trading API v2. Uses the paper or live endpoint depending on {@code alpaca.paper}. */
@Component
public class AlpacaBrokerClient implements BrokerClient {

    private final RestClient client;
    private final AlpacaProperties props;

    public AlpacaBrokerClient(AlpacaProperties props, RestClient.Builder builder) {
        this.props = props;
        this.client = builder.clone()
                .baseUrl(props.tradingUrl())
                .defaultHeader("APCA-API-KEY-ID", nullToEmpty(props.keyId()))
                .defaultHeader("APCA-API-SECRET-KEY", nullToEmpty(props.secretKey()))
                .build();
    }

    @Override
    public Account account() {
        requireConfigured();
        return client.get().uri("/v2/account").retrieve().body(Account.class);
    }

    @Override
    public List<Position> positions() {
        requireConfigured();
        Position[] p = client.get().uri("/v2/positions").retrieve().body(Position[].class);
        return p == null ? List.of() : Arrays.asList(p);
    }

    @Override
    public Optional<Position> position(String symbol) {
        requireConfigured();
        try {
            return Optional.ofNullable(client.get().uri("/v2/positions/{s}", symbol).retrieve().body(Position.class));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public List<Order> orders(String status, int limit) {
        requireConfigured();
        return client.get()
                .uri(u -> u.path("/v2/orders").queryParam("status", status).queryParam("limit", limit)
                        .queryParam("direction", "desc").build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Order>>() { });
    }

    @Override
    public Order submit(NewOrder order) {
        requireConfigured();
        return client.post().uri("/v2/orders").contentType(MediaType.APPLICATION_JSON).body(order)
                .retrieve().body(Order.class);
    }

    @Override
    public void cancel(String orderId) {
        requireConfigured();
        client.delete().uri("/v2/orders/{id}", orderId).retrieve().toBodilessEntity();
    }

    @Override
    public Order closePosition(String symbol) {
        requireConfigured();
        return client.delete().uri("/v2/positions/{s}", symbol).retrieve().body(Order.class);
    }

    @Override
    public Clock clock() {
        requireConfigured();
        return client.get().uri("/v2/clock").retrieve().body(Clock.class);
    }

    private void requireConfigured() {
        if (!props.configured()) {
            throw new BrokerNotConfiguredException();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
