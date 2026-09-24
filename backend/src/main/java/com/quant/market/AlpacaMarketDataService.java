package com.quant.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.quant.config.AlpacaProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/** Market data from https://data.alpaca.markets (v2 stocks API). */
public class AlpacaMarketDataService implements MarketDataService {

    private final RestClient client;
    private final String feed;

    public AlpacaMarketDataService(AlpacaProperties props, RestClient.Builder builder) {
        this.client = builder.clone()
                .baseUrl(props.dataUrl())
                .defaultHeader("APCA-API-KEY-ID", props.keyId())
                .defaultHeader("APCA-API-SECRET-KEY", props.secretKey())
                .build();
        this.feed = props.feed();
    }

    @Override
    public List<Bar> bars(String symbol, Timeframe timeframe, LocalDate from, LocalDate to) {
        List<Bar> bars = new ArrayList<>();
        String pageToken = null;
        do {
            String token = pageToken;
            JsonNode body = client.get()
                    .uri(uri -> {
                        UriBuilder b = uri.path("/v2/stocks/{symbol}/bars")
                                .queryParam("timeframe", timeframe.alpaca())
                                .queryParam("start", from.atStartOfDay(ZoneOffset.UTC).toInstant())
                                .queryParam("end", to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                                .queryParam("adjustment", "all")
                                .queryParam("feed", feed)
                                .queryParam("limit", 10000);
                        if (token != null) {
                            b.queryParam("page_token", token);
                        }
                        return b.build(symbol);
                    })
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                break;
            }
            for (JsonNode n : body.path("bars")) {
                bars.add(new Bar(Instant.parse(n.get("t").asText()),
                        n.get("o").asDouble(), n.get("h").asDouble(), n.get("l").asDouble(), n.get("c").asDouble(),
                        n.get("v").asLong()));
            }
            JsonNode next = body.get("next_page_token");
            pageToken = next == null || next.isNull() ? null : next.asText();
        } while (pageToken != null);
        return bars;
    }

    @Override
    public Map<String, Quote> quotes(List<String> symbols) {
        Map<String, Quote> out = new LinkedHashMap<>();
        if (symbols.isEmpty()) {
            return out;
        }
        JsonNode body = client.get()
                .uri(uri -> uri.path("/v2/stocks/snapshots")
                        .queryParam("symbols", String.join(",", symbols))
                        .queryParam("feed", feed)
                        .build())
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            return out;
        }
        for (String symbol : symbols) {
            JsonNode snap = body.get(symbol);
            if (snap == null || snap.isNull()) {
                continue;
            }
            JsonNode trade = snap.path("latestTrade");
            double price = trade.path("p").asDouble(snap.path("dailyBar").path("c").asDouble());
            double prevClose = snap.path("prevDailyBar").path("c").asDouble(price);
            Instant time = trade.hasNonNull("t") ? Instant.parse(trade.get("t").asText()) : Instant.now();
            out.put(symbol, Quote.of(symbol, price, prevClose, time));
        }
        return out;
    }

    @Override
    public String source() {
        return "alpaca";
    }
}
