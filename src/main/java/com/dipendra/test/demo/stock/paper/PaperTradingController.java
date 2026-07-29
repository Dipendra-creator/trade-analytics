package com.dipendra.test.demo.stock.paper;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paper")
public class PaperTradingController {
    private final PaperTradingService service;

    public PaperTradingController(PaperTradingService service) { this.service = service; }

    @GetMapping("/portfolio")
    public ResponseEntity<PaperTradingService.PaperPortfolio> portfolio() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.portfolio());
    }

    @GetMapping("/trades")
    public ResponseEntity<List<PaperTradeView>> trades() {
        List<PaperTradeView> result = service.recentTrades().stream().map(PaperTradeView::from).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    public record PaperTradeView(Long id, String symbol, String side, String state, java.time.Instant signalAt,
            java.time.Instant openedAt, java.time.Instant closedAt, java.math.BigDecimal entryPrice,
            java.math.BigDecimal stopPrice, java.math.BigDecimal targetPrice, java.math.BigDecimal exitPrice,
            int quantity, java.math.BigDecimal netPnl, String exitReason) {
        static PaperTradeView from(PaperTrade trade) {
            return new PaperTradeView(trade.getId(), trade.getSymbol(), trade.getSide(), trade.getState(),
                    trade.getSignalAt(), trade.getOpenedAt(), trade.getClosedAt(), trade.getEntryPrice(),
                    trade.getStopPrice(), trade.getTargetPrice(), trade.getExitPrice(), trade.getQuantity(),
                    trade.getNetPnl(), trade.getExitReason());
        }
    }
}
