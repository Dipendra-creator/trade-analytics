package com.dipendra.test.demo.stock.analytics;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.BreadthMetrics;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.ForecastMetrics;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.IndexMetrics;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.SectorImpact;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.StockImpact;
import com.dipendra.test.demo.stock.config.DhanProperties;

@Service
public class NiftyQuantEngine {
    private static final String COMPONENT_QUERY = """
            SELECT nc.rank_number, nc.symbol, nc.stock_name, nc.sector, nc.weight_percent,
                   current_bar.close_price AS price,
                   COALESCE((SELECT p.close_price FROM stock_candle p
                     WHERE p.constituent_id=nc.id AND p.interval_start < DATE(current_bar.interval_start)
                     ORDER BY p.interval_start DESC LIMIT 1), current_bar.open_price) AS previous_close,
                   COALESCE((SELECT p.close_price FROM stock_candle p WHERE p.constituent_id=nc.id
                     AND p.interval_start <= current_bar.interval_start - INTERVAL 5 MINUTE
                     ORDER BY p.interval_start DESC LIMIT 1), current_bar.close_price) AS price_5m,
                   COALESCE((SELECT p.close_price FROM stock_candle p WHERE p.constituent_id=nc.id
                     AND p.interval_start <= current_bar.interval_start - INTERVAL 15 MINUTE
                     ORDER BY p.interval_start DESC LIMIT 1), current_bar.close_price) AS price_15m,
                   COALESCE((SELECT p.close_price FROM stock_candle p WHERE p.constituent_id=nc.id
                     AND p.interval_start <= current_bar.interval_start - INTERVAL 60 MINUTE
                     ORDER BY p.interval_start DESC LIMIT 1), current_bar.close_price) AS price_60m
            FROM nifty50_constituent nc
            JOIN stock_candle current_bar ON current_bar.constituent_id=nc.id
             AND current_bar.interval_start=(SELECT MAX(last_bar.interval_start) FROM stock_candle last_bar
                                              WHERE last_bar.constituent_id=nc.id)
            WHERE nc.active=TRUE ORDER BY nc.rank_number
            """;

    private final JdbcTemplate jdbc;
    private final DhanIndexMarketService indexMarket;
    private final DhanProperties properties;

    public NiftyQuantEngine(JdbcTemplate jdbc, DhanIndexMarketService indexMarket, DhanProperties properties) {
        this.jdbc = jdbc;
        this.indexMarket = indexMarket;
        this.properties = properties;
    }

    public MarketAnalyticsSnapshot calculate() {
        List<Component> components = jdbc.query(COMPONENT_QUERY, (rs, row) -> new Component(
                rs.getInt("rank_number"), rs.getString("symbol"), rs.getString("stock_name"),
                rs.getString("sector"), rs.getDouble("weight_percent"), rs.getDouble("price"),
                rs.getDouble("previous_close"), rs.getDouble("price_5m"),
                rs.getDouble("price_15m"), rs.getDouble("price_60m")));
        double weightTotal = components.stream().mapToDouble(Component::weight).sum();
        IndexMarketState market = indexMarket.current();
        double previousIndex = market.previousClose().doubleValue();
        if (previousIndex <= 0) previousIndex = market.level().doubleValue();

        List<RawImpact> raw = new ArrayList<>();
        double weightedReturn = 0, weighted5 = 0, weighted15 = 0, weighted60 = 0;
        int advances = 0, declines = 0, unchanged = 0;
        for (Component c : components) {
            double w = weightTotal == 0 ? 0 : c.weight() / weightTotal;
            double day = safeReturn(c.price(), c.previousClose());
            double r5 = safeReturn(c.price(), c.price5m());
            double r15 = safeReturn(c.price(), c.price15m());
            double r60 = safeReturn(c.price(), c.price60m());
            weightedReturn += w * day;
            weighted5 += w * r5;
            weighted15 += w * r15;
            weighted60 += w * r60;
            if (day > 0.00005) advances++; else if (day < -0.00005) declines++; else unchanged++;
            raw.add(new RawImpact(c, w, day, r5, r15, r60));
        }

        double syntheticLevel = previousIndex > 0 ? previousIndex * (1 + weightedReturn) : 0;
        double officialLevel = market.level().doubleValue() > 0 ? market.level().doubleValue() : syntheticLevel;
        double actualChange = previousIndex > 0 ? officialLevel - previousIndex : 0;
        final double indexBase = previousIndex;
        final double fiveMinuteBasket = weighted5;
        double absoluteImpact = raw.stream().mapToDouble(r -> abs(indexBase * r.weight() * r.dayReturn())).sum();
        List<StockImpact> stocks = new ArrayList<>();
        Map<String, SectorAccumulator> sectorMap = new LinkedHashMap<>();
        double dispersionVariance = 0;
        for (RawImpact r : raw) {
            double points = previousIndex * r.weight() * r.dayReturn();
            double impactShare = absoluteImpact == 0 ? 0 : abs(points) / absoluteImpact;
            double risk = r.weight() * abs(r.r5()) * 100;
            String signal = points > 0.35 ? "LIFTING" : points < -0.35 ? "DRAGGING" : "NEUTRAL";
            stocks.add(new StockImpact(r.component().rank(), r.component().symbol(), r.component().name(),
                    r.component().sector(), r.weight() * 100, r.component().price(), r.component().previousClose(),
                    r.dayReturn() * 100, points, impactShare * 100, r.r5() * 100, r.r15() * 100,
                    r.r60() * 100, risk, signal));
            dispersionVariance += r.weight() * Math.pow(r.r5() - weighted5, 2);
            sectorMap.computeIfAbsent(r.component().sector(), ignored -> new SectorAccumulator())
                    .add(r.weight(), r.dayReturn(), points);
        }
        double dispersion = Math.sqrt(max(0, dispersionVariance));
        double breadthScore = components.isEmpty() ? 0 : (advances - declines) / (double) components.size();
        double projected15m = 0.50 * weighted5 * 3.0 + 0.30 * weighted15 + 0.20 * weighted60 * 0.25;
        double breadthAdjustment = breadthScore * min(0.0006, dispersion * 0.12);
        double forecastReturn = clamp(projected15m * 0.65 + breadthAdjustment, -0.015, 0.015);
        double expectedPoints = officialLevel * forecastReturn;
        double rangePoints = officialLevel * max(0.0008, dispersion * Math.sqrt(3.0) * 0.55);
        double signAgreement = raw.isEmpty() ? 0 : raw.stream()
                .filter(r -> Math.signum(r.r5()) == Math.signum(fiveMinuteBasket)).count() / (double) raw.size();
        double confidence = clamp(0.25 + 0.35 * signAgreement + 0.20 * abs(breadthScore)
                - 0.10 * min(1, dispersion / 0.01), 0.10, 0.85);

        List<SectorImpact> sectors = sectorMap.entrySet().stream()
                .map(e -> e.getValue().toResponse(e.getKey()))
                .sorted(Comparator.comparingDouble((SectorImpact s) -> abs(s.contributionPoints())).reversed())
                .toList();
        double topFive = stocks.stream().sorted(Comparator.comparingDouble(StockImpact::impactShare).reversed())
                .limit(5).mapToDouble(StockImpact::impactShare).sum();
        stocks.sort(Comparator.comparingDouble(StockImpact::contributionPoints).reversed());

        return new MarketAnalyticsSnapshot(Instant.now(), marketStatus(), components.size(),
                new IndexMetrics(officialLevel, previousIndex, actualChange,
                        previousIndex == 0 ? 0 : actualChange / previousIndex * 100,
                        syntheticLevel, officialLevel - syntheticLevel, previousIndex * weightedReturn),
                new ForecastMetrics("15_MINUTES", direction(expectedPoints), expectedPoints,
                        officialLevel + expectedPoints - rangePoints, officialLevel + expectedPoints + rangePoints,
                        confidence * 100, projected15m * 10_000, dispersion * 100),
                new BreadthMetrics(advances, declines, unchanged, breadthScore,
                        absoluteImpact, topFive), sectors, stocks);
    }

    private String marketStatus() {
        ZonedDateTime now = ZonedDateTime.now(properties.getMarketZone());
        boolean weekday = now.getDayOfWeek() != DayOfWeek.SATURDAY && now.getDayOfWeek() != DayOfWeek.SUNDAY;
        LocalTime time = now.toLocalTime();
        return weekday && !time.isBefore(properties.getMarketOpen()) && time.isBefore(properties.getMarketClose())
                ? "OPEN" : "CLOSED";
    }

    private static double safeReturn(double current, double base) { return base <= 0 ? 0 : current / base - 1; }
    private static double clamp(double value, double low, double high) { return max(low, min(high, value)); }
    private static String direction(double points) { return points > 1 ? "UP" : points < -1 ? "DOWN" : "SIDEWAYS"; }

    private record Component(int rank, String symbol, String name, String sector, double weight,
            double price, double previousClose, double price5m, double price15m, double price60m) { }
    private record RawImpact(Component component, double weight, double dayReturn, double r5, double r15, double r60) { }

    private static final class SectorAccumulator {
        double weight, weightedReturn, points;
        int advances, declines;
        void add(double w, double r, double p) { weight += w; weightedReturn += w * r; points += p;
            if (r > 0.00005) advances++; else if (r < -0.00005) declines++; }
        SectorImpact toResponse(String sector) { return new SectorImpact(sector, weight * 100,
                weight == 0 ? 0 : weightedReturn / weight * 100, points, advances, declines); }
    }
}
