package com.dipendra.test.demo.stock.service;

import java.math.BigDecimal;
import java.time.Instant;

public record LatestQuote(
        String securityId,
        String symbol,
        BigDecimal lastPrice,
        int lastQuantity,
        long dayVolume,
        BigDecimal averagePrice,
        BigDecimal dayOpen,
        BigDecimal dayHigh,
        BigDecimal dayLow,
        BigDecimal dayClose,
        Instant tradedAt) {
}
