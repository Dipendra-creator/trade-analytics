package com.dipendra.test.demo.stock.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaperTradeRepository extends JpaRepository<PaperTrade, Long> {
    List<PaperTrade> findByStateOrderByOpenedAtAsc(String state);
    List<PaperTrade> findTop100ByOrderByOpenedAtDesc();
    boolean existsBySignalKey(String signalKey);
    boolean existsBySymbolAndState(String symbol, String state);

    @Query("select coalesce(sum(t.netPnl), 0) from PaperTrade t where t.state='CLOSED' and t.closedAt >= :from")
    BigDecimal realizedPnlSince(@Param("from") Instant from);

    @Query("select coalesce(sum(t.netPnl), 0) from PaperTrade t where t.state='CLOSED'")
    BigDecimal totalRealizedPnl();

    long countByState(String state);
}
