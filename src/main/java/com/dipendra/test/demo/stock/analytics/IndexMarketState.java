package com.dipendra.test.demo.stock.analytics;

import java.math.BigDecimal;
import java.time.Instant;

public record IndexMarketState(
        BigDecimal level,
        BigDecimal previousClose,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        Instant updatedAt) {
    public static IndexMarketState empty() {
        return new IndexMarketState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, Instant.EPOCH);
    }
}
