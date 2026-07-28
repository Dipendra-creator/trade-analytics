package com.dipendra.test.demo.stock.analytics;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

@Service
public class LiveAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(LiveAnalyticsService.class);

    private final NiftyQuantEngine engine;
    private final Timer calculationTimer;
    private final Counter failureCounter;
    private final AtomicReference<MarketAnalyticsSnapshot> latest = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessfulCalculation = new AtomicReference<>();

    public LiveAnalyticsService(NiftyQuantEngine engine) {
        this(engine, Metrics.globalRegistry);
    }

    @Autowired
    public LiveAnalyticsService(NiftyQuantEngine engine, MeterRegistry registry) {
        this.engine = engine;
        this.calculationTimer = registry.timer("analytics_calculation_duration");
        this.failureCounter = registry.counter("analytics_calculation_failures_total");
    }

    @Scheduled(fixedDelayString = "${analytics.refresh-ms:1000}", initialDelay = 0)
    public void refresh() {
        try {
            calculationTimer.record(() -> {
                MarketAnalyticsSnapshot snapshot = engine.calculate();
                latest.set(snapshot);
                lastSuccessfulCalculation.set(Instant.now());
            });
        } catch (RuntimeException exception) {
            failureCounter.increment();
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
