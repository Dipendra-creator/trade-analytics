package com.dipendra.test.demo.stock.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "nifty50_constituent")
public class Nifty50Constituent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rank_number", nullable = false)
    private Integer rank;

    @Column(name = "security_id", nullable = false, length = 20)
    private String securityId;

    @Column(nullable = false, length = 40)
    private String symbol;

    @Column(name = "stock_name", nullable = false, length = 150)
    private String stockName;

    @Column(nullable = false, length = 100)
    private String sector;

    @Column(name = "weight_percent", nullable = false, precision = 6, scale = 3)
    private BigDecimal weightPercent;

    @Column(name = "exchange_segment", nullable = false, length = 20)
    private String exchangeSegment;

    @Column(name = "instrument_type", nullable = false, length = 20)
    private String instrumentType;

    @Column(nullable = false)
    private boolean active;

    protected Nifty50Constituent() {
    }

    public Long getId() { return id; }
    public Integer getRank() { return rank; }
    public String getSecurityId() { return securityId; }
    public String getSymbol() { return symbol; }
    public String getStockName() { return stockName; }
    public String getSector() { return sector; }
    public BigDecimal getWeightPercent() { return weightPercent; }
    public String getExchangeSegment() { return exchangeSegment; }
    public String getInstrumentType() { return instrumentType; }
    public boolean isActive() { return active; }
}
