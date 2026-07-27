package com.dipendra.test.demo.stock.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HistoricalCandle(
        LocalDateTime intervalStart,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume) {
}
