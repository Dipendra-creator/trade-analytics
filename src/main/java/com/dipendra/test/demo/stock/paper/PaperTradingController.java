package com.dipendra.test.demo.stock.paper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.dipendra.test.demo.stock.analytics.LiveAnalyticsService;
import com.dipendra.test.demo.stock.analytics.MarketAnalyticsSnapshot.StockImpact;

@RestController
@RequestMapping("/api/paper")
public class PaperTradingController {
    private final PaperTradingService service;
    private final LiveAnalyticsService analytics;

    public PaperTradingController(PaperTradingService service, LiveAnalyticsService analytics) {
        this.service = service;
        this.analytics = analytics;
    }

    @GetMapping("/portfolio")
    public ResponseEntity<PaperTradingService.PaperPortfolio> portfolio() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.portfolio());
    }

    @GetMapping("/trades")
    public ResponseEntity<List<PaperTradeView>> trades() {
        List<PaperTradeView> result = views(100);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<PaperTradeView>> recent(@RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(views(Math.max(1, Math.min(20, limit))));
    }

    private List<PaperTradeView> views(int limit) {
        Map<String, Double> prices = analytics.latest().stream().flatMap(value -> value.stocks().stream())
                .collect(Collectors.toMap(StockImpact::symbol, StockImpact::price, (a, b) -> a));
        return service.recentTrades().stream().limit(limit).map(trade -> PaperTradeView.from(trade, prices.get(trade.getSymbol()))).toList();
    }

    public record PaperTradeView(Long id, String symbol, String side, String state, java.time.Instant signalAt,
            java.time.Instant openedAt, java.time.Instant closedAt, java.math.BigDecimal entryPrice,
            java.math.BigDecimal stopPrice, java.math.BigDecimal targetPrice, java.math.BigDecimal exitPrice,
            java.math.BigDecimal currentPrice, int quantity, java.math.BigDecimal netPnl,
            java.math.BigDecimal livePnl, long heldSeconds, double targetProgress, String exitReason) {
        static PaperTradeView from(PaperTrade trade, Double livePrice) {
            java.math.BigDecimal current = "OPEN".equals(trade.getState()) && livePrice != null
                    ? java.math.BigDecimal.valueOf(livePrice) : trade.getExitPrice();
            java.math.BigDecimal livePnl = trade.getNetPnl();
            if (livePnl == null && current != null) {
                java.math.BigDecimal direction = "LONG".equals(trade.getSide())
                        ? java.math.BigDecimal.ONE : java.math.BigDecimal.ONE.negate();
                livePnl = current.subtract(trade.getEntryPrice()).multiply(direction)
                        .multiply(java.math.BigDecimal.valueOf(trade.getQuantity()));
            }
            java.time.Instant end = trade.getClosedAt() == null ? java.time.Instant.now() : trade.getClosedAt();
            long held = Math.max(0, java.time.Duration.between(trade.getOpenedAt(), end).toSeconds());
            double progress = 0;
            if (current != null) {
                double total = trade.getTargetPrice().subtract(trade.getEntryPrice()).doubleValue();
                if (total != 0) progress = Math.max(0, Math.min(100,
                        current.subtract(trade.getEntryPrice()).doubleValue() / total * 100));
            }
            return new PaperTradeView(trade.getId(), trade.getSymbol(), trade.getSide(), trade.getState(),
                    trade.getSignalAt(), trade.getOpenedAt(), trade.getClosedAt(), trade.getEntryPrice(),
                    trade.getStopPrice(), trade.getTargetPrice(), trade.getExitPrice(), current, trade.getQuantity(),
                    trade.getNetPnl(), livePnl, held, progress, trade.getExitReason());
        }
    }
}
