package com.dipendra.test.demo.stock.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "stock_candle")
public class StockCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "constituent_id", nullable = false)
    private Nifty50Constituent constituent;

    @Column(name = "interval_start", nullable = false)
    private LocalDateTime intervalStart;
    @Column(name = "open_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal open;
    @Column(name = "high_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal high;
    @Column(name = "low_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal low;
    @Column(name = "close_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal close;
    @Column(nullable = false)
    private long volume;
    @Column(nullable = false, length = 20)
    private String source;

    protected StockCandle() {
    }

    public LocalDateTime getIntervalStart() { return intervalStart; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public long getVolume() { return volume; }
    public String getSource() { return source; }
}
