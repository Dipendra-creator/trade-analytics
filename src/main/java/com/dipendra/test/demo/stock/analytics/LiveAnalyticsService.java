package com.dipendra.test.demo.stock.analytics;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class LiveAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(LiveAnalyticsService.class);

    private final NiftyQuantEngine engine;
    private final AtomicReference<MarketAnalyticsSnapshot> latest = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulCalculation = new AtomicReference<>();

    public LiveAnalyticsService(NiftyQuantEngine engine) {
        this.engine = engine;
    }

    @Scheduled(fixedDelayString = "${analytics.refresh-ms:1000}", initialDelay = 0)
    public void refresh() {
        try {
            MarketAnalyticsSnapshot snapshot = engine.calculate();
            latest.set(snapshot);
            lastSuccessfulCalculation.set(Instant.now());
        } catch (RuntimeException exception) {
            log.warn("Could not refresh continuous analytics: {}", exception.getMessage());
        }
    }

    public Optional<MarketAnalyticsSnapshot> latest() {
        return Optional.ofNullable(latest.get());
    }

    public Optional<Instant> lastSuccessfulCalculation() {
        return Optional.ofNullable(lastSuccessfulCalculation.get());
    }
}
