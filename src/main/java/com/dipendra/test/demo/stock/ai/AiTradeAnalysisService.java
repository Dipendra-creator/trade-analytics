package com.dipendra.test.demo.stock.ai;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static java.lang.Math.min;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.dipendra.test.demo.settings.AppSettingsService;
import com.dipendra.test.demo.stock.ai.AiAnalysisSnapshot.TradeCandidate;
import com.dipendra.test.demo.stock.analytics.LiveAnalyticsService;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.StockImpact;

import tools.jackson.databind.ObjectMapper;

@Service
public class AiTradeAnalysisService {
    private static final Logger log = LoggerFactory.getLogger(AiTradeAnalysisService.class);
    private static final Duration NEW_WINDOW = Duration.ofMinutes(3);

    private final LiveAnalyticsService analytics;
    private final AppSettingsService settings;
    private final RestClient openAi;
    private final ObjectMapper objectMapper;
    private final String model;
    private final AtomicReference<Narrative> narrative = new AtomicReference<>();
    private final Map<String, Instant> firstSeen = new ConcurrentHashMap<>();

    public AiTradeAnalysisService(LiveAnalyticsService analytics, AppSettingsService settings,
            ObjectMapper objectMapper,
            @Value("${openai.api-base-url:https://api.openai.com}") String apiBaseUrl,
            @Value("${openai.model:gpt-5.6-terra}") String model) {
        this.analytics = analytics;
        this.settings = settings;
        this.objectMapper = objectMapper;
        this.model = model;
        this.openAi = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    public Optional<AiAnalysisSnapshot> latest() {
        return analytics.latest().map(this::buildSnapshot);
    }

    @Scheduled(fixedDelayString = "${openai.analysis-refresh-ms:60000}", initialDelay = 10_000)
    public void refreshAiNarrative() {
        Optional<MarketAnalyticsSnapshot> current = analytics.latest();
        if (current.isEmpty()) return;
        Optional<String> apiKey = settings.getOpenAiApiKey();
        if (apiKey.isEmpty()) {
            narrative.set(null);
            return;
        }
        try {
            MarketAnalyticsSnapshot market = current.get();
            List<TradeCandidate> candidates = rankCandidates(market);
            String prompt = buildPrompt(market, candidates);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", model);
            request.put("reasoning", Map.of("effort", "low"));
            request.put("max_output_tokens", 700);
            request.put("input", prompt);
            Map<String, Object> response = openAi.post().uri("/v1/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.get())
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            Narrative generated = parseNarrative(extractOutputText(response));
            narrative.set(new Narrative("OPENAI", model, generated.regime(), generated.summary(),
                    generated.riskNote(), market.timestamp()));
        } catch (RuntimeException exception) {
            log.warn("OpenAI market narrative refresh failed: {}", exception.getMessage());
            Narrative old = narrative.get();
            if (old == null) narrative.set(new Narrative("AI_ERROR", model, "Live market",
                    "Quant signals remain live while AI commentary is temporarily unavailable.",
                    "Validate liquidity, spread, and position size before acting.", current.get().timestamp()));
        }
    }

    private AiAnalysisSnapshot buildSnapshot(MarketAnalyticsSnapshot market) {
        List<TradeCandidate> candidates = rankCandidates(market);
        Narrative ai = narrative.get();
        boolean configured = settings.getOpenAiApiKey().isPresent();
        boolean currentAi = ai != null && !ai.marketDataTimestamp().isBefore(market.timestamp().minusSeconds(180));
        String mode = currentAi ? ai.mode() : configured ? "AI_PENDING" : "QUANT_ONLY";
        String regime = currentAi ? ai.regime() : deterministicRegime(market);
        String summary = currentAi ? ai.summary() : deterministicSummary(market, candidates);
        String risk = currentAi ? ai.riskNote()
                : "Signals are model outputs, not investment advice. Confirm price, liquidity, and risk before placing an order.";
        return new AiAnalysisSnapshot(Instant.now(), market.timestamp(), market.marketStatus(), mode,
                currentAi ? ai.model() : model, regime, summary, risk, candidates);
    }

    List<TradeCandidate> rankCandidates(MarketAnalyticsSnapshot market) {
        Instant now = Instant.now();
        List<ScoredStock> ranked = new ArrayList<>();
        for (StockImpact stock : market.stocks()) {
            if (stock.price() <= 0) continue;
            double momentum = .50 * stock.return5m() + .32 * stock.return15m() + .18 * stock.return60m();
            if (abs(momentum) < .035 || abs(stock.return5m()) < .015) continue;
            String side = momentum >= 0 ? "LONG" : "SHORT";
            int agreement = sign(stock.return5m()) == sign(momentum) ? 1 : 0;
            agreement += sign(stock.return15m()) == sign(momentum) ? 1 : 0;
            agreement += sign(stock.return60m()) == sign(momentum) ? 1 : 0;
            double breadthAlignment = sign(market.breadth().score()) == sign(momentum) ? 5 : 0;
            double confidence = clamp(42 + agreement * 9 + min(18, abs(momentum) * 18)
                    + breadthAlignment - min(8, stock.riskScore() * 12), 35, 88);
            double score = abs(momentum) * confidence + abs(stock.contributionPoints()) * 2
                    + stock.impactShare() * .08;
            ranked.add(new ScoredStock(stock, side, momentum, confidence, score));
        }
        ranked.sort(Comparator.comparingDouble(ScoredStock::score).reversed());
        List<TradeCandidate> result = new ArrayList<>();
        for (ScoredStock item : ranked.stream().limit(6).toList()) {
            StockImpact stock = item.stock();
            String key = stock.symbol() + ":" + item.side();
            Instant seen = firstSeen.computeIfAbsent(key, ignored -> now);
            double volatilityPercent = max(.22, abs(stock.return5m()) * 1.7 + abs(stock.return15m()) * .55);
            double stopDistance = stock.price() * clamp(volatilityPercent / 100, .0022, .012);
            double rewardDistance = stopDistance * 1.8;
            boolean isLong = "LONG".equals(item.side());
            double stop = stock.price() + (isLong ? -stopDistance : stopDistance);
            double target = stock.price() + (isLong ? rewardDistance : -rewardDistance);
            String thesis = thesis(stock, item.side());
            result.add(new TradeCandidate(stock.symbol(), stock.name(), stock.sector(), item.side(),
                    Duration.between(seen, now).compareTo(NEW_WINDOW) < 0 ? "NEW" : "ACTIVE",
                    round(stock.price()), round(stop), round(target), 1.8, round(item.confidence()),
                    round(item.score()), stock.return5m(), stock.return15m(), stock.return60m(),
                    stock.contributionPoints(), thesis, seen));
        }
        return List.copyOf(result);
    }

    private String buildPrompt(MarketAnalyticsSnapshot market, List<TradeCandidate> candidates) {
        Map<String, Object> evidence = Map.of(
                "marketStatus", market.marketStatus(),
                "index", market.index(),
                "forecast", market.forecast(),
                "breadth", market.breadth(),
                "topSectors", market.sectors().stream().limit(5).toList(),
                "quantCandidates", candidates);
        String json = objectMapper.writeValueAsString(evidence);
        return """
                You are a cautious NSE intraday market analyst. Analyze only the supplied live quantitative evidence.
                Do not invent prices, news, fundamentals, or events. The deterministic engine owns every trade level.
                Return only a JSON object with exactly these string fields: regime, summary, riskNote.
                regime: at most 8 words. summary: at most 55 words and explain the strongest current setup and market context.
                riskNote: at most 35 words and identify the most important invalidation or concentration risk.
                Never promise profit. State uncertainty plainly. Evidence follows:
                """ + json;
    }

    private Narrative parseNarrative(String text) {
        String cleaned = text == null ? "" : text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = objectMapper.readValue(cleaned, Map.class);
            return new Narrative("OPENAI", model,
                    value(parsed, "regime", "Live market"),
                    value(parsed, "summary", "AI analysis completed without a summary."),
                    value(parsed, "riskNote", "Use predefined risk levels and verify execution conditions."), Instant.now());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("OpenAI returned an invalid analysis payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractOutputText(Map<String, Object> response) {
        if (response == null) return "";
        Object direct = response.get("output_text");
        if (direct instanceof String text) return text;
        Object outputValue = response.get("output");
        if (!(outputValue instanceof List<?> output)) return "";
        for (Object itemValue : output) {
            if (!(itemValue instanceof Map<?, ?> item)) continue;
            Object contentValue = item.get("content");
            if (!(contentValue instanceof List<?> content)) continue;
            for (Object partValue : content) {
                if (partValue instanceof Map<?, ?> part && part.get("text") instanceof String text) return text;
            }
        }
        return "";
    }

    private static String deterministicRegime(MarketAnalyticsSnapshot market) {
        if (market.breadth().score() > .3) return "Broad positive participation";
        if (market.breadth().score() < -.3) return "Broad negative pressure";
        if (market.breadth().topFiveImpactShare() > 60) return "Concentrated index leadership";
        return "Balanced rotational market";
    }

    private static String deterministicSummary(MarketAnalyticsSnapshot market, List<TradeCandidate> candidates) {
        if (candidates.isEmpty()) return "No setup currently clears the momentum and agreement filters. The live engine will surface candidates when conditions strengthen.";
        TradeCandidate lead = candidates.get(0);
        return lead.symbol() + " ranks first for a " + lead.side().toLowerCase()
                + " setup from multi-window momentum and index contribution. Market breadth is "
                + market.breadth().advances() + " advancing versus " + market.breadth().declines() + " declining.";
    }

    private static String thesis(StockImpact stock, String side) {
        String contribution = stock.contributionPoints() >= 0 ? "lifting" : "dragging";
        return side + " momentum aligns across live windows; the stock is " + contribution + " Nifty by "
                + round(abs(stock.contributionPoints())) + " points.";
    }

    private static String value(Map<String, Object> values, String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : fallback;
    }

    private static int sign(double value) { return value > 0 ? 1 : value < 0 ? -1 : 0; }
    private static double clamp(double value, double low, double high) { return max(low, min(high, value)); }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private record ScoredStock(StockImpact stock, String side, double momentum, double confidence, double score) { }
    private record Narrative(String mode, String model, String regime, String summary, String riskNote,
            Instant marketDataTimestamp) { }
}
