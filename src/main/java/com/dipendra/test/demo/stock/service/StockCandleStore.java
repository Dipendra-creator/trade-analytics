package com.dipendra.test.demo.stock.service;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StockCandleStore {
    private static final String HISTORICAL_UPSERT = """
            INSERT INTO stock_candle
                (constituent_id, interval_start, open_price, high_price, low_price, close_price, volume, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'DHAN_HISTORICAL') AS incoming
            ON DUPLICATE KEY UPDATE
                open_price = incoming.open_price, high_price = incoming.high_price,
                low_price = incoming.low_price, close_price = incoming.close_price,
                volume = incoming.volume, source = incoming.source
            """;

    private static final String LIVE_UPSERT = """
            INSERT INTO stock_candle
                (constituent_id, interval_start, open_price, high_price, low_price, close_price, volume, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'DHAN_LIVE') AS incoming
            ON DUPLICATE KEY UPDATE
                high_price = GREATEST(stock_candle.high_price, incoming.high_price),
                low_price = LEAST(stock_candle.low_price, incoming.low_price),
                close_price = incoming.close_price,
                volume = stock_candle.volume + incoming.volume,
                source = 'DHAN_LIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public StockCandleStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveHistorical(long constituentId, HistoricalCandle candle) {
        jdbcTemplate.update(HISTORICAL_UPSERT, constituentId, Timestamp.valueOf(candle.intervalStart()),
                candle.open(), candle.high(), candle.low(), candle.close(), candle.volume());
    }

    public void saveHistoricalBatch(long constituentId, List<HistoricalCandle> candles) {
        jdbcTemplate.batchUpdate(HISTORICAL_UPSERT, candles, 500,
                (statement, candle) -> bindHistorical(statement, constituentId, candle));
    }

    public void applyLiveTick(long constituentId, LocalDateTime minute, BigDecimal price, long volumeDelta) {
        jdbcTemplate.update(LIVE_UPSERT, constituentId, Timestamp.valueOf(minute),
                price, price, price, price, volumeDelta);
    }

    private static void bindHistorical(
            PreparedStatement statement, long constituentId, HistoricalCandle candle) throws SQLException {
        statement.setLong(1, constituentId);
        statement.setTimestamp(2, Timestamp.valueOf(candle.intervalStart()));
        statement.setBigDecimal(3, candle.open());
        statement.setBigDecimal(4, candle.high());
        statement.setBigDecimal(5, candle.low());
        statement.setBigDecimal(6, candle.close());
        statement.setLong(7, candle.volume());
    }
}
