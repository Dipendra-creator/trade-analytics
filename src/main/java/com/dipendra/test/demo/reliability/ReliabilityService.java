package com.dipendra.test.demo.reliability;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Service;

import com.dipendra.test.demo.stock.analytics.LiveAnalyticsService;
import com.dipendra.test.demo.stock.paper.PaperTradingService;
import com.dipendra.test.demo.stock.service.DhanLiveFeedService;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class ReliabilityService implements HealthIndicator {
    private final LiveAnalyticsService analytics;
    private final DhanLiveFeedService feed;
    private final PaperTradingService paper;

    public ReliabilityService(LiveAnalyticsService analytics, DhanLiveFeedService feed,
            PaperTradingService paper, MeterRegistry registry) {
        this.analytics = analytics;
        this.feed = feed;
        this.paper = paper;
        Gauge.builder("analytics_snapshot_age_seconds", this, ReliabilityService::snapshotAgeSeconds)
                .register(registry);
        Gauge.builder("dhan_feed_age_seconds", this, ReliabilityService::feedAgeSeconds)
                .register(registry);
        Gauge.builder("market_open", this, value -> value.analytics.latest()
                .filter(snapshot -> "OPEN".equals(snapshot.marketStatus())).isPresent() ? 1 : 0)
                .register(registry);
    }

    public ReliabilitySummary summary() {
        Instant now = Instant.now();
        var snapshot = analytics.latest().orElse(null);
        DhanLiveFeedService.FeedStatus feedStatus = feed.status();
        double snapshotAge = age(snapshot == null ? null : snapshot.timestamp(), now);
        double feedAge = age(feedStatus.lastPacketAt(), now);
        boolean marketOpen = snapshot != null && "OPEN".equals(snapshot.marketStatus());
        boolean fresh = !marketOpen || snapshotAge <= 3;
        String status = fresh ? "HEALTHY" : "DEGRADED";
        return new ReliabilitySummary(now, status, ManagementFactory.getRuntimeMXBean().getUptime(),
                snapshot == null ? "STARTING" : snapshot.marketStatus(), snapshotAge,
                snapshot == null ? 0 : snapshot.coverage(), feedStatus.connected(), feedAge,
                feedStatus.packetsReceived(), feedStatus.connectionAttempts(), paper.portfolio());
    }

    @Override
    public Health health() {
        ReliabilitySummary status = summary();
        Health.Builder result = "HEALTHY".equals(status.status()) ? Health.up() : Health.status("DEGRADED");
        return result.withDetail("marketStatus", status.marketStatus())
                .withDetail("analyticsAgeSeconds", status.analyticsAgeSeconds())
                .withDetail("coverage", status.coverage())
                .withDetail("dhanConnected", status.dhanConnected()).build();
    }

    private double snapshotAgeSeconds() {
        Optional<Instant> at = analytics.latest().map(value -> value.timestamp());
        return at.map(value -> age(value, Instant.now())).orElse(Double.NaN);
    }

    private double feedAgeSeconds() { return age(feed.status().lastPacketAt(), Instant.now()); }

    private static double age(Instant value, Instant now) {
        return value == null ? -1 : Math.max(0, Duration.between(value, now).toMillis() / 1000.0);
    }

    public record ReliabilitySummary(Instant timestamp, String status, long uptimeMillis, String marketStatus,
            double analyticsAgeSeconds, int coverage, boolean dhanConnected, double dhanFeedAgeSeconds,
            long packetsReceived, long connectionAttempts, PaperTradingService.PaperPortfolio paper) { }
}
