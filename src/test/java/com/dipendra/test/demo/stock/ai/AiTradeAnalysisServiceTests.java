package com.dipendra.test.demo.stock.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.dipendra.test.demo.settings.AppSettingsService;
import com.dipendra.test.demo.stock.analytics.LiveAnalyticsService;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.BreadthMetrics;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.ForecastMetrics;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.IndexMetrics;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.StockImpact;

import tools.jackson.databind.json.JsonMapper;

class AiTradeAnalysisServiceTests {
    @Test
    void ranksMomentumAndKeepsLongLevelsOrdered() {
        AiTradeAnalysisService service = new AiTradeAnalysisService(mock(LiveAnalyticsService.class),
                mock(AppSettingsService.class), JsonMapper.builder().build(), "https://api.openai.com", "test-model");
        StockImpact stock = new StockImpact(1, "RELIANCE", "Reliance Industries", "Energy", 10, 3000, 2950,
                1.69, 12, 22, .25, .40, .65, .08, "LIFTING");
        MarketAnalyticsSnapshot market = new MarketAnalyticsSnapshot(Instant.now(), "OPEN", 1,
                new IndexMetrics(25000, 24900, 100, .4, 25000, 0, 12),
                new ForecastMetrics("15_MINUTES", "UP", 10, 24980, 25040, 70, 4, .2),
                new BreadthMetrics(32, 18, 0, .28, 40, 50), List.of(), List.of(stock));

        List<AiAnalysisSnapshot.TradeCandidate> candidates = service.rankCandidates(market);

        assertThat(candidates).hasSize(1);
        AiAnalysisSnapshot.TradeCandidate candidate = candidates.get(0);
        assertThat(candidate.side()).isEqualTo("LONG");
        assertThat(candidate.stop()).isLessThan(candidate.entry());
        assertThat(candidate.target()).isGreaterThan(candidate.entry());
        assertThat(candidate.state()).isEqualTo("NEW");
    }
}
