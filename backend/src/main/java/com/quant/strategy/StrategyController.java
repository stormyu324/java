package com.quant.strategy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StrategyController {

    @GetMapping("/api/strategies")
    public List<StrategyInfo> list() {
        return Arrays.stream(StrategyType.values())
                .map(t -> new StrategyInfo(t, t.label(), t.description(), t.defaults()))
                .toList();
    }

    public record StrategyInfo(StrategyType type, String label, String description, Map<String, Double> defaults) {
    }
}
