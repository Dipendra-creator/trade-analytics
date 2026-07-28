package com.dipendra.test.demo.stock.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class DhanQuotePacketParserTests {
    @Test
    void parsesQuotePacketUsingDhanLittleEndianLayout() {
        ByteBuffer packet = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
        packet.put((byte) 4);
        packet.putShort((short) 50);
        packet.put((byte) 1);
        packet.putInt(1333);
        packet.putFloat(1_650.25f);
        packet.putShort((short) 12);
        packet.putInt(1_785_124_800);
        packet.putFloat(1_640.50f);
        packet.putInt(100_000);
        packet.putInt(20_000);
        packet.putInt(21_000);
        packet.putFloat(1_630.00f);
        packet.putFloat(0.0f);
        packet.putFloat(1_660.00f);
        packet.putFloat(1_625.00f);

        List<DhanQuotePacketParser.ParsedQuote> result = DhanQuotePacketParser.parse(packet.array());

        assertThat(result).hasSize(1);
        DhanQuotePacketParser.ParsedQuote quote = result.get(0);
        assertThat(quote.securityId()).isEqualTo("1333");
        assertThat(quote.lastPrice()).isEqualByComparingTo("1650.25");
        assertThat(quote.lastQuantity()).isEqualTo(12);
        assertThat(quote.dayVolume()).isEqualTo(100_000);
        assertThat(quote.tradedAt()).isEqualTo(Instant.ofEpochSecond(1_785_124_800));
        assertThat(quote.dayHigh()).isEqualByComparingTo("1660.0");
    }

    @Test
    void normalizesDhanMarketWallClockToActualInstant() {
        Instant encodedMarketWallClock = Instant.parse("2026-07-28T12:41:58Z");

        Instant normalized = DhanLiveFeedService.normalizeDhanTradeTime(
                encodedMarketWallClock, ZoneId.of("Asia/Kolkata"));

        assertThat(normalized).isEqualTo(Instant.parse("2026-07-28T07:11:58Z"));
    }
}
