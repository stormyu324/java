package com.quant.bot;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyBotRepository extends JpaRepository<StrategyBot, Long> {

    List<StrategyBot> findByEnabledTrue();

    Optional<StrategyBot> findBySymbol(String symbol);
}
