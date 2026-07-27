package com.dipendra.test.demo.stock.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dipendra.test.demo.stock.domain.StockCandle;

public interface StockCandleRepository extends JpaRepository<StockCandle, Long> {
    List<StockCandle> findByConstituentSecurityIdAndIntervalStartBetweenOrderByIntervalStartAsc(
            String securityId, LocalDateTime from, LocalDateTime to);
}
