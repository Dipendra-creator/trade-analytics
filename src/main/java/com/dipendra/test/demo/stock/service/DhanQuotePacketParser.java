package com.dipendra.test.demo.stock.service;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class DhanQuotePacketParser {
    private static final int HEADER_SIZE = 8;
    private static final int QUOTE_PACKET_SIZE = 50;
    private static final int QUOTE_RESPONSE_CODE = 4;

    private DhanQuotePacketParser() {
    }

    static List<ParsedQuote> parse(byte[] message) {
        List<ParsedQuote> quotes = new ArrayList<>();
        int offset = 0;
        while (offset + HEADER_SIZE <= message.length) {
            ByteBuffer packet = ByteBuffer.wrap(message, offset, message.length - offset)
                    .slice().order(ByteOrder.LITTLE_ENDIAN);
            int responseCode = Byte.toUnsignedInt(packet.get(0));
            int packetLength = Short.toUnsignedInt(packet.getShort(1));
            if (packetLength < HEADER_SIZE || offset + packetLength > message.length) {
                break;
            }
            if (responseCode == QUOTE_RESPONSE_CODE && packetLength >= QUOTE_PACKET_SIZE) {
                String securityId = Integer.toUnsignedString(packet.getInt(4));
                quotes.add(new ParsedQuote(
                        securityId,
                        decimal(packet.getFloat(8)),
                        Short.toUnsignedInt(packet.getShort(12)),
                        Instant.ofEpochSecond(Integer.toUnsignedLong(packet.getInt(14))),
                        decimal(packet.getFloat(18)),
                        Integer.toUnsignedLong(packet.getInt(22)),
                        decimal(packet.getFloat(34)),
                        decimal(packet.getFloat(38)),
                        decimal(packet.getFloat(42)),
                        decimal(packet.getFloat(46))));
            }
            offset += packetLength;
        }
        return quotes;
    }

    private static BigDecimal decimal(float value) {
        return new BigDecimal(Float.toString(value));
    }

    record ParsedQuote(
            String securityId,
            BigDecimal lastPrice,
            int lastQuantity,
            Instant tradedAt,
            BigDecimal averagePrice,
            long dayVolume,
            BigDecimal dayOpen,
            BigDecimal dayClose,
            BigDecimal dayHigh,
            BigDecimal dayLow) {
    }
}
