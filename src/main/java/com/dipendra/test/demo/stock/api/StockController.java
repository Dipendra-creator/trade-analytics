package com.dipendra.test.demo.stock.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.domain.Nifty50Constituent;
import com.dipendra.test.demo.stock.domain.StockCandle;
import com.dipendra.test.demo.stock.repository.Nifty50Repository;
import com.dipendra.test.demo.stock.repository.StockCandleRepository;
import com.dipendra.test.demo.stock.service.LatestQuote;
import com.dipendra.test.demo.stock.service.LatestQuoteStore;

@RestController
@RequestMapping("/api/nifty50")
public class StockController {
    private final Nifty50Repository stockRepository;
    private final StockCandleRepository candleRepository;
    private final LatestQuoteStore latestQuoteStore;
    private final DhanProperties properties;

    public StockController(Nifty50Repository stockRepository, StockCandleRepository candleRepository,
            LatestQuoteStore latestQuoteStore, DhanProperties properties) {
        this.stockRepository = stockRepository;
        this.candleRepository = candleRepository;
        this.latestQuoteStore = latestQuoteStore;
        this.properties = properties;
    }

    @GetMapping
    public List<ConstituentResponse> constituents() {
        return stockRepository.findByActiveTrueOrderByRankAsc().stream().map(ConstituentResponse::from).toList();
    }

    @GetMapping("/{symbol}/latest")
    public LatestQuote latest(@PathVariable String symbol) {
        Nifty50Constituent stock = requireStock(symbol);
        return latestQuoteStore.get(stock.getSecurityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No live quote is cached yet"));
    }

    @GetMapping("/{symbol}/candles")
    public List<CandleResponse> candles(
            @PathVariable String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        Nifty50Constituent stock = requireStock(symbol);
        LocalDateTime effectiveFrom = from == null ? properties.getBackfillFrom() : from;
        LocalDateTime effectiveTo = to == null
                ? ZonedDateTime.now(properties.getMarketZone()).toLocalDateTime() : to;
        if (!effectiveFrom.isBefore(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        return candleRepository
                .findByConstituentSecurityIdAndIntervalStartBetweenOrderByIntervalStartAsc(
                        stock.getSecurityId(), effectiveFrom, effectiveTo)
                .stream().map(CandleResponse::from).toList();
    }

    private Nifty50Constituent requireStock(String symbol) {
        return stockRepository.findBySymbolIgnoreCaseAndActiveTrue(symbol)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown Nifty 50 symbol"));
    }

    public record ConstituentResponse(
            int rank, String securityId, String symbol, String name, String sector, BigDecimal weightPercent) {
        static ConstituentResponse from(Nifty50Constituent stock) {
            return new ConstituentResponse(stock.getRank(), stock.getSecurityId(), stock.getSymbol(),
                    stock.getStockName(), stock.getSector(), stock.getWeightPercent());
        }
    }

    public record CandleResponse(
            LocalDateTime intervalStart, BigDecimal open, BigDecimal high, BigDecimal low,
            BigDecimal close, long volume, String source) {
        static CandleResponse from(StockCandle candle) {
            return new CandleResponse(candle.getIntervalStart(), candle.getOpen(), candle.getHigh(),
                    candle.getLow(), candle.getClose(), candle.getVolume(), candle.getSource());
        }
    }
}
