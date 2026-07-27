package com.dipendra.test.demo.stock.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.domain.Nifty50Constituent;

@Component
public class DhanHistoricalClient {
    private static final DateTimeFormatter DHAN_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ParameterizedTypeReference<Map<String, List<Number>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final DhanProperties properties;
    private final RestClient restClient;

    public DhanHistoricalClient(DhanProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }

    public List<HistoricalCandle> fetchIntraday(
            Nifty50Constituent stock, LocalDateTime from, LocalDateTime to) {
        Map<String, Object> request = Map.of(
                "securityId", stock.getSecurityId(),
                "exchangeSegment", stock.getExchangeSegment(),
                "instrument", stock.getInstrumentType(),
                "interval", "1",
                "oi", false,
                "fromDate", DHAN_DATE_TIME.format(from),
                "toDate", DHAN_DATE_TIME.format(to));

        Map<String, List<Number>> response = restClient.post()
                .uri("/charts/intraday")
                .header("access-token", properties.getAccessToken())
                .body(request)
                .retrieve()
                .body(RESPONSE_TYPE);
        if (response == null) {
            return List.of();
        }
        return mapCandles(response);
    }

    private List<HistoricalCandle> mapCandles(Map<String, List<Number>> response) {
        List<Number> timestamps = required(response, "timestamp");
        List<Number> opens = required(response, "open");
        List<Number> highs = required(response, "high");
        List<Number> lows = required(response, "low");
        List<Number> closes = required(response, "close");
        List<Number> volumes = required(response, "volume");
        int size = timestamps.size();
        if (opens.size() != size || highs.size() != size || lows.size() != size
                || closes.size() != size || volumes.size() != size) {
            throw new IllegalStateException("Dhan historical response arrays have different lengths");
        }

        List<HistoricalCandle> candles = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            LocalDateTime time = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamps.get(index).longValue()), properties.getMarketZone());
            candles.add(new HistoricalCandle(time, decimal(opens.get(index)), decimal(highs.get(index)),
                    decimal(lows.get(index)), decimal(closes.get(index)), volumes.get(index).longValue()));
        }
        return candles;
    }

    private static List<Number> required(Map<String, List<Number>> response, String name) {
        List<Number> value = response.get(name);
        if (value == null) {
            throw new IllegalStateException("Dhan historical response is missing " + name);
        }
        return value;
    }

    private static BigDecimal decimal(Number value) {
        return new BigDecimal(value.toString());
    }
}
