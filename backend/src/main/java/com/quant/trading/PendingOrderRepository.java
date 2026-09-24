package com.quant.trading;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingOrderRepository extends JpaRepository<PendingOrder, Long> {

    List<PendingOrder> findByStatus(PendingOrder.Status status);

    List<PendingOrder> findBySourceAndSymbolAndStatus(String source, String symbol, PendingOrder.Status status);

    List<PendingOrder> findTop100ByOrderByCreatedAtDesc();
}
