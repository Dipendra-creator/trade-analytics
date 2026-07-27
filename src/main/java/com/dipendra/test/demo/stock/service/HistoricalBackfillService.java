package com.dipendra.test.demo.stock.service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.domain.Nifty50Constituent;
import com.dipendra.test.demo.stock.repository.Nifty50Repository;

@Service
public class HistoricalBackfillService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalBackfillService.class);
    private static final long REQUEST_PAUSE_MILLIS = 225L;

    private final DhanProperties properties;
    private final Nifty50Repository stockRepository;
    private final DhanHistoricalClient client;
    private final StockCandleStore candleStore;

    public HistoricalBackfillService(DhanProperties properties, Nifty50Repository stockRepository,
            DhanHistoricalClient client, StockCandleStore candleStore) {
        this.properties = properties;
        this.stockRepository = stockRepository;
        this.client = client;
        this.candleStore = candleStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationIsReady() {
        if (!properties.isBackfillEnabled()) {
            log.info("Dhan historical backfill is disabled");
            return;
        }
        if (!properties.hasCredentials()) {
            log.warn("Skipping Dhan historical backfill: DHAN_CLIENT_ID and DHAN_ACCESS_TOKEN are not configured");
            return;
        }
        CompletableFuture.runAsync(this::backfillAll)
                .exceptionally(error -> {
                    log.error("Unexpected failure in Dhan historical backfill", error);
                    return null;
                });
    }

    void backfillAll() {
        LocalDateTime from = properties.getBackfillFrom();
        // Do not let a historical snapshot overwrite the minute currently being built by live ticks.
        LocalDateTime now = ZonedDateTime.now(properties.getMarketZone()).toLocalDateTime()
                .truncatedTo(ChronoUnit.MINUTES).minusMinutes(1);
        LocalDateTime to = properties.getBackfillTo() == null ? now : properties.getBackfillTo();
        if (from == null || !from.isBefore(to)) {
            log.warn("Skipping historical backfill because the configured time range is empty");
            return;
        }

        List<Nifty50Constituent> stocks = stockRepository.findByActiveTrueOrderByRankAsc();
        log.info("Starting Dhan one-minute backfill for {} stocks from {} to {}", stocks.size(), from, to);
        int completed = 0;
        for (Nifty50Constituent stock : stocks) {
            try {
                List<HistoricalCandle> candles = client.fetchIntraday(stock, from, to);
                candleStore.saveHistoricalBatch(stock.getId(), candles);
                completed++;
                log.info("Backfilled {} candles for {} ({}/{})", candles.size(), stock.getSymbol(), completed, stocks.size());
            } catch (RuntimeException exception) {
                log.error("Dhan backfill failed for {}: {}", stock.getSymbol(), exception.getMessage());
            }
            if (!pauseForRateLimit()) {
                return;
            }
        }
        log.info("Dhan historical backfill finished: {}/{} stocks completed", completed, stocks.size());
    }

    private boolean pauseForRateLimit() {
        try {
            Thread.sleep(REQUEST_PAUSE_MILLIS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.info("Dhan historical backfill was interrupted");
            return false;
        }
    }
}
