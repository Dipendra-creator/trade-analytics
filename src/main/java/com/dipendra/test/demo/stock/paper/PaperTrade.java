package com.dipendra.test.demo.stock.paper;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "paper_trade")
public class PaperTrade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "signal_key", nullable = false, unique = true, length = 160)
    private String signalKey;
    @Column(name = "algorithm_version", nullable = false, length = 40)
    private String algorithmVersion;
    @Column(nullable = false, length = 24)
    private String symbol;
    @Column(nullable = false, length = 8)
    private String side;
    @Column(nullable = false, length = 20)
    private String state;
    @Column(name = "signal_at", nullable = false)
    private Instant signalAt;
    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "entry_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal entryPrice;
    @Column(name = "stop_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal stopPrice;
    @Column(name = "target_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal targetPrice;
    @Column(name = "exit_price", precision = 18, scale = 4)
    private BigDecimal exitPrice;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false, precision = 8, scale = 4)
    private BigDecimal confidence;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal score;
    @Column(name = "gross_pnl", precision = 18, scale = 4)
    private BigDecimal grossPnl;
    @Column(precision = 18, scale = 4)
    private BigDecimal costs;
    @Column(name = "net_pnl", precision = 18, scale = 4)
    private BigDecimal netPnl;
    @Column(name = "exit_reason", length = 24)
    private String exitReason;
    @Column(name = "evidence_json", nullable = false, columnDefinition = "json")
    private String evidenceJson;

    protected PaperTrade() { }

    public static PaperTrade open(String signalKey, String symbol, String side, Instant signalAt,
            BigDecimal entry, BigDecimal stop, BigDecimal target, int quantity,
            BigDecimal confidence, BigDecimal score, String evidenceJson) {
        PaperTrade trade = new PaperTrade();
        trade.signalKey = signalKey;
        trade.algorithmVersion = "quant-v1";
        trade.symbol = symbol;
        trade.side = side;
        trade.state = "OPEN";
        trade.signalAt = signalAt;
        trade.openedAt = Instant.now();
        trade.entryPrice = entry;
        trade.stopPrice = stop;
        trade.targetPrice = target;
        trade.quantity = quantity;
        trade.confidence = confidence;
        trade.score = score;
        trade.evidenceJson = evidenceJson;
        return trade;
    }

    public void close(Instant at, BigDecimal exit, BigDecimal transactionCosts, String reason) {
        BigDecimal direction = "LONG".equals(side) ? BigDecimal.ONE : BigDecimal.ONE.negate();
        this.closedAt = at;
        this.exitPrice = exit;
        this.costs = transactionCosts;
        this.grossPnl = exit.subtract(entryPrice).multiply(direction).multiply(BigDecimal.valueOf(quantity));
        this.netPnl = grossPnl.subtract(transactionCosts);
        this.exitReason = reason;
        this.state = "CLOSED";
    }

    public Long getId() { return id; }
    public String getSignalKey() { return signalKey; }
    public String getAlgorithmVersion() { return algorithmVersion; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public String getState() { return state; }
    public Instant getSignalAt() { return signalAt; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getClosedAt() { return closedAt; }
    public BigDecimal getEntryPrice() { return entryPrice; }
    public BigDecimal getStopPrice() { return stopPrice; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public BigDecimal getExitPrice() { return exitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal getConfidence() { return confidence; }
    public BigDecimal getScore() { return score; }
    public BigDecimal getGrossPnl() { return grossPnl; }
    public BigDecimal getCosts() { return costs; }
    public BigDecimal getNetPnl() { return netPnl; }
    public String getExitReason() { return exitReason; }
    public String getEvidenceJson() { return evidenceJson; }
}
