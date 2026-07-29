package com.dipendra.test.demo.stock.paper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class PaperTradeTests {
    @Test
    void calculatesLongNetPnlAfterCosts() {
        PaperTrade trade = PaperTrade.open("signal-1", "RELIANCE", "LONG", Instant.now(),
                new BigDecimal("100.00"), new BigDecimal("98.00"), new BigDecimal("103.60"),
                100, new BigDecimal("72"), new BigDecimal("15"), "{}");

        trade.close(Instant.now(), new BigDecimal("103.60"), new BigDecimal("20.00"), "TARGET");

        assertThat(trade.getGrossPnl()).isEqualByComparingTo("360.00");
        assertThat(trade.getNetPnl()).isEqualByComparingTo("340.00");
        assertThat(trade.getState()).isEqualTo("CLOSED");
        assertThat(trade.getExitReason()).isEqualTo("TARGET");
    }

    @Test
    void calculatesShortNetPnlAfterCosts() {
        PaperTrade trade = PaperTrade.open("signal-2", "HDFCBANK", "SHORT", Instant.now(),
                new BigDecimal("200.00"), new BigDecimal("202.00"), new BigDecimal("196.40"),
                50, new BigDecimal("68"), new BigDecimal("12"), "{}");

        trade.close(Instant.now(), new BigDecimal("196.40"), new BigDecimal("10.00"), "TARGET");

        assertThat(trade.getGrossPnl()).isEqualByComparingTo("180.00");
        assertThat(trade.getNetPnl()).isEqualByComparingTo("170.00");
    }
}
