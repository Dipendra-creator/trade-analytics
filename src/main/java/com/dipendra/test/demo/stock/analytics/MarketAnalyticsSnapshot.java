package com.dipendra.test.demo.stock.analytics;

import java.time.Instant;
import java.util.List;

public record MarketAnalyticsSnapshot(
        Instant timestamp,
        Instant dataTimestamp,
        String marketStatus,
        int coverage,
        IndexMetrics index,
        ForecastMetrics forecast,
        BreadthMetrics breadth,
        List<SectorImpact> sectors,
        List<StockImpact> stocks) {

    public record IndexMetrics(double level, double previousClose, double changePoints, double changePercent,
            double syntheticLevel, double trackingDifference, double totalAttributedPoints) { }
    public record ForecastMetrics(String horizon, String direction, double expectedPoints, double lowerBound,
            double upperBound, double confidence, double momentumScore, double dispersion) { }
    public record BreadthMetrics(int advances, int declines, int unchanged, double score,
            double absoluteImpactPoints, double topFiveImpactShare) { }
    public record SectorImpact(String sector, double weight, double returnPercent, double contributionPoints,
            int advances, int declines) { }
    public record StockImpact(int rank, String symbol, String name, String sector, double weight,
            double price, double previousClose, double returnPercent, double contributionPoints,
            double impactShare, double return5m, double return15m, double return60m,
            double riskScore, String signal) { }
}
