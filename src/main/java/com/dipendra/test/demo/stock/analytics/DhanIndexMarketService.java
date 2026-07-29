package com.dipendra.test.demo.stock.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.config.ExternalRestClientFactory;

@Service
public class DhanIndexMarketService {
    private static final Logger log = LoggerFactory.getLogger(DhanIndexMarketService.class);
    private final DhanProperties properties;
    private final RestClient client;
    private final AtomicReference<IndexMarketState> state = new AtomicReference<>(IndexMarketState.empty());
    private volatile LocalDate historicalRefreshDate;

    public DhanIndexMarketService(DhanProperties properties, ExternalRestClientFactory restClients) {
        this.properties = properties;
        this.client = restClients.create(properties.getApiBaseUrl());
    }

    public IndexMarketState current() {
        return state.get();
    }

    @Scheduled(fixedDelay = 5_000, initialDelay = 3_000)
    public void refresh() {
        if (!properties.hasCredentials()) return;
        try {
            LocalDate today = LocalDate.now(properties.getMarketZone());
            if (!today.equals(historicalRefreshDate)) {
                refreshPreviousClose(today);
                historicalRefreshDate = today;
            }
            Map<String, Object> response = client.post().uri("/marketfeed/ohlc")
                    .header("access-token", properties.getAccessToken())
                    .header("client-id", properties.getClientId())
                    .body(Map.of("IDX_I", List.of(13)))
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            Map<String, Object> quote = nested(response, "data", "IDX_I", "13");
            Map<String, Object> ohlc = map(quote.get("ohlc"));
            IndexMarketState old = state.get();
            state.set(new IndexMarketState(decimal(quote.get("last_price")), old.previousClose(),
                    decimal(ohlc.get("open")), decimal(ohlc.get("high")), decimal(ohlc.get("low")), Instant.now()));
        } catch (RuntimeException exception) {
            log.warn("Could not refresh Nifty index snapshot: {}", exception.getMessage());
        }
    }

    private void refreshPreviousClose(LocalDate today) {
        Map<String, Object> request = Map.of("securityId", "13", "exchangeSegment", "IDX_I",
                "instrument", "INDEX", "expiryCode", 0, "oi", false,
                "fromDate", today.minusDays(12).toString(), "toDate", today.plusDays(1).toString());
        Map<String, List<Number>> response = client.post().uri("/charts/historical")
                .header("access-token", properties.getAccessToken()).body(request).retrieve()
                .body(new ParameterizedTypeReference<>() { });
        List<Number> closes = response == null ? null : response.get("close");
        if (closes != null && !closes.isEmpty()) {
            IndexMarketState old = state.get();
            state.set(new IndexMarketState(old.level(), decimal(closes.get(closes.size() - 1)),
                    old.open(), old.high(), old.low(), old.updatedAt()));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nested(Map<String, Object> root, String... keys) {
        Map<String, Object> value = root;
        for (String key : keys) value = map(value.get(key));
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }
}
