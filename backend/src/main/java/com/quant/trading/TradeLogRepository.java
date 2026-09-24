package com.quant.trading;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeLogRepository extends JpaRepository<TradeLog, Long> {

    List<TradeLog> findTop100ByOrderByTimeDesc();
}
