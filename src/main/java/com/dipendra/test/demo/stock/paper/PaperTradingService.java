package com.dipendra.test.demo.stock.paper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dipendra.test.demo.stock.ai.AiAnalysisSnapshot;
import com.dipendra.test.demo.stock.ai.AiAnalysisSnapshot.TradeCandidate;
import com.dipendra.test.demo.stock.ai.AiTradeAnalysisService;
import com.dipendra.test.demo.stock.analytics.LiveAnalyticsService;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.StockImpact;
import com.dipendra.test.demo.stock.config.DhanProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaperTradingService {
    private static final Logger log = LoggerFactory.getLogger(PaperTradingService.class);
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");

    private final PaperTradeRepository repository;
    private final PaperTradingProperties properties;
    private final AiTradeAnalysisService candidates;
    private final LiveAnalyticsService analytics;
    private final DhanProperties dhan;
    private final ObjectMapper objectMapper;
    private final TelegramTradeNotifier telegram;
    private final Counter openedCounter;
    private final Counter closedCounter;

    public PaperTradingService(PaperTradeRepository repository, PaperTradingProperties properties,
            AiTradeAnalysisService candidates, LiveAnalyticsService analytics, DhanProperties dhan,
            ObjectMapper objectMapper, TelegramTradeNotifier telegram, MeterRegistry registry) {
        this.repository = repository;
        this.properties = properties;
        this.candidates = candidates;
        this.analytics = analytics;
        this.dhan = dhan;
        this.objectMapper = objectMapper;
        this.telegram = telegram;
        this.openedCounter = registry.counter("paper_trades_opened_total");
        this.closedCounter = registry.counter("paper_trades_closed_total");
        Gauge.builder("paper_positions_open", repository, value -> value.countByState("OPEN"))
                .register(registry);
        Gauge.builder("paper_risk_halted", this, value -> value.dailyLossLimitReached() ? 1 : 0)
                .register(registry);
    }

    @Scheduled(fixedDelay = 1_000, initialDelay = 15_000)
    @Transactional
    public void evaluate() {
        if (!properties.isEnabled()) return;
        try {
            var market = analytics.latest().orElse(null);
            if (market == null || !"OPEN".equals(market.marketStatus())) return;
            if (Duration.between(market.dataTimestamp(), Instant.now()).abs().toSeconds() > 3) return;
            Map<String, Double> prices = market.stocks().stream()
                    .collect(Collectors.toMap(StockImpact::symbol, StockImpact::price, (a, b) -> a));
            closeEligible(prices);
            AiAnalysisSnapshot snapshot = candidates.latest().orElse(null);
            if (snapshot != null) openEligible(snapshot);
        } catch (RuntimeException exception) {
            log.warn("Paper trading evaluation failed: {}", exception.getMessage());
        }
    }

    private void closeEligible(Map<String, Double> prices) {
        Instant now = Instant.now();
        ZonedDateTime marketNow = now.atZone(dhan.getMarketZone());
        for (PaperTrade trade : repository.findByStateOrderByOpenedAtAsc("OPEN")) {
            Double rawPrice = prices.get(trade.getSymbol());
            if (rawPrice == null || rawPrice <= 0) continue;
            BigDecimal price = money(rawPrice);
            String reason = exitReason(trade, price, now, marketNow.toLocalTime());
            if (reason == null) continue;
            BigDecimal exit = applySlippage(price, trade.getSide(), false);
            BigDecimal notional = trade.getEntryPrice().add(exit)
                    .multiply(BigDecimal.valueOf(trade.getQuantity()));
            BigDecimal costs = notional.multiply(properties.getTransactionCostBpsPerSide())
                    .divide(TEN_THOUSAND, 4, RoundingMode.HALF_UP);
            trade.close(now, exit, costs, reason);
            repository.save(trade);
            closedCounter.increment();
            telegram.closed(trade);
        }
    }

    private String exitReason(PaperTrade trade, BigDecimal price, Instant now, LocalTime marketTime) {
        boolean longTrade = "LONG".equals(trade.getSide());
        if (longTrade && price.compareTo(trade.getStopPrice()) <= 0
                || !longTrade && price.compareTo(trade.getStopPrice()) >= 0) return "STOP";
        if (longTrade && price.compareTo(trade.getTargetPrice()) >= 0
                || !longTrade && price.compareTo(trade.getTargetPrice()) <= 0) return "TARGET";
        if (Duration.between(trade.getOpenedAt(), now).toMinutes() >= properties.getMaxHoldMinutes()) return "TIME";
        if (!marketTime.isBefore(LocalTime.of(15, 20))) return "MARKET_CLOSE";
        return null;
    }

    private void openEligible(AiAnalysisSnapshot snapshot) {
        List<PaperTrade> open = repository.findByStateOrderByOpenedAtAsc("OPEN");
        if (open.size() >= properties.getMaxOpenPositions() || dailyLossLimitReached()) return;
        for (TradeCandidate candidate : snapshot.candidates()) {
            if (open.size() >= properties.getMaxOpenPositions()) break;
            if (repository.existsBySymbolAndState(candidate.symbol(), "OPEN")) continue;
            Instant seenAt = candidate.firstSeenAt() == null ? snapshot.marketDataTimestamp() : candidate.firstSeenAt();
            String signalKey = candidate.symbol() + ":" + candidate.side() + ":" + seenAt.truncatedTo(ChronoUnit.SECONDS);
            if (repository.existsBySignalKey(signalKey)) continue;
            BigDecimal rawEntry = money(candidate.entry());
            BigDecimal entry = applySlippage(rawEntry, candidate.side(), true);
            BigDecimal stop = money(candidate.stop());
            BigDecimal riskPerShare = entry.subtract(stop).abs();
            if (riskPerShare.signum() == 0) continue;
            BigDecimal riskBudget = currentEquity().multiply(properties.getRiskPerTrade());
            int quantity = riskBudget.divide(riskPerShare, 0, RoundingMode.DOWN).intValue();
            int affordable = currentEquity().divide(entry, 0, RoundingMode.DOWN).intValue();
            quantity = Math.min(quantity, affordable);
            if (quantity < 1) continue;
            try {
                String evidence = objectMapper.writeValueAsString(Map.of(
                        "marketDataTimestamp", snapshot.marketDataTimestamp(),
                        "analysisMode", snapshot.analysisMode(), "candidate", candidate));
                PaperTrade trade = PaperTrade.open(signalKey, candidate.symbol(), candidate.side(), seenAt,
                        entry, stop, money(candidate.target()), quantity, money(candidate.confidence()),
                        money(candidate.score()), evidence);
                repository.saveAndFlush(trade);
                open.add(trade);
                openedCounter.increment();
                telegram.opened(trade);
            } catch (DataIntegrityViolationException ignored) {
                log.debug("Paper signal {} was already recorded", signalKey);
            }
        }
    }

    private boolean dailyLossLimitReached() {
        Instant dayStart = ZonedDateTime.now(dhan.getMarketZone()).toLocalDate()
                .atStartOfDay(dhan.getMarketZone()).toInstant();
        BigDecimal pnl = repository.realizedPnlSince(dayStart);
        return pnl.compareTo(properties.getStartingCapital().multiply(properties.getMaxDailyLoss()).negate()) <= 0;
    }

    public BigDecimal currentEquity() {
        return properties.getStartingCapital().add(repository.totalRealizedPnl());
    }

    private BigDecimal applySlippage(BigDecimal price, String side, boolean entry) {
        boolean adverseUp = entry == "LONG".equals(side);
        BigDecimal factor = properties.getSlippageBps().divide(TEN_THOUSAND, 8, RoundingMode.HALF_UP);
        return price.multiply(adverseUp ? BigDecimal.ONE.add(factor) : BigDecimal.ONE.subtract(factor))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    public PaperPortfolio portfolio() {
        List<PaperTrade> recent = repository.findTop100ByOrderByOpenedAtDesc();
        long wins = recent.stream().filter(t -> t.getNetPnl() != null && t.getNetPnl().signum() > 0).count();
        long losses = recent.stream().filter(t -> t.getNetPnl() != null && t.getNetPnl().signum() < 0).count();
        BigDecimal grossProfit = recent.stream().map(PaperTrade::getNetPnl).filter(v -> v != null && v.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossLoss = recent.stream().map(PaperTrade::getNetPnl).filter(v -> v != null && v.signum() < 0)
                .map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? BigDecimal.ZERO
                : grossProfit.divide(grossLoss, 4, RoundingMode.HALF_UP);
        return new PaperPortfolio(properties.getStartingCapital(), currentEquity(),
                repository.countByState("OPEN"), wins, losses, profitFactor, dailyLossLimitReached());
    }

    public List<PaperTrade> recentTrades() { return repository.findTop100ByOrderByOpenedAtDesc(); }

    @Scheduled(fixedRate = 900_000, initialDelay = 30_000)
    public void sendLatestTradeStatus() {
        Map<String, Double> prices = analytics.latest().stream().flatMap(value -> value.stocks().stream())
                .collect(Collectors.toMap(StockImpact::symbol, StockImpact::price, (a, b) -> a));
        telegram.status(repository.findTop100ByOrderByOpenedAtDesc().stream().limit(3).toList(), prices);
    }

    public record PaperPortfolio(BigDecimal startingCapital, BigDecimal currentEquity, long openPositions,
            long wins, long losses, BigDecimal profitFactor, boolean riskHalted) { }
}
