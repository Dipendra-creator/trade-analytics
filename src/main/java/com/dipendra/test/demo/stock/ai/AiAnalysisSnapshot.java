package com.dipendra.test.demo.stock.ai;

import java.time.Instant;
import java.util.List;

public record AiAnalysisSnapshot(
        Instant timestamp,
        Instant marketDataTimestamp,
        String marketStatus,
        String analysisMode,
        String model,
        String regime,
        String summary,
        String riskNote,
        List<TradeCandidate> candidates) {

    public record TradeCandidate(
            String symbol,
            String name,
            String sector,
            String side,
            String state,
            double entry,
            double stop,
            double target,
            double riskReward,
            double confidence,
            double score,
            double return5m,
            double return15m,
            double return60m,
            double niftyContribution,
            String thesis,
            Instant firstSeenAt) { }
}
