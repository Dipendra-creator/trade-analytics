package com.dipendra.test.demo.stock.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class LiveAnalyticsServiceTests {
    @Test
    void refreshCachesSnapshotWithoutAnyWebSocketSessions() {
        NiftyQuantEngine engine = mock(NiftyQuantEngine.class);
        MarketAnalyticsSnapshot expected = new MarketAnalyticsSnapshot(Instant.now(), "OPEN", 0,
                new MarketAnalyticsSnapshot.IndexMetrics(0, 0, 0, 0, 0, 0, 0),
                new MarketAnalyticsSnapshot.ForecastMetrics("15_MINUTES", "SIDEWAYS", 0, 0, 0, 0, 0, 0),
                new MarketAnalyticsSnapshot.BreadthMetrics(0, 0, 0, 0, 0, 0), List.of(), List.of());
        when(engine.calculate()).thenReturn(expected);

        LiveAnalyticsService service = new LiveAnalyticsService(engine);
        service.refresh();

        assertThat(service.latest()).contains(expected);
        assertThat(service.lastSuccessfulCalculation()).isPresent();
    }
}
