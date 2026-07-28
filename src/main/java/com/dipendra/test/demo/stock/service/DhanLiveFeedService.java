package com.dipendra.test.demo.stock.service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.dipendra.test.demo.stock.config.DhanProperties;
import com.dipendra.test.demo.stock.domain.Nifty50Constituent;
import com.dipendra.test.demo.stock.repository.Nifty50Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import jakarta.annotation.PreDestroy;

@Service
public class DhanLiveFeedService {
    private static final Logger log = LoggerFactory.getLogger(DhanLiveFeedService.class);
    private static final int QUOTE_SUBSCRIBE_CODE = 17;

    private final DhanProperties properties;
    private final Nifty50Repository stockRepository;
    private final StockCandleStore candleStore;
    private final LatestQuoteStore quoteStore;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final Map<String, Nifty50Constituent> stocksBySecurityId = new ConcurrentHashMap<>();
    private final Map<String, VolumeState> volumes = new ConcurrentHashMap<>();
    private volatile WebSocket socket;

    public DhanLiveFeedService(DhanProperties properties, Nifty50Repository stockRepository,
            StockCandleStore candleStore, LatestQuoteStore quoteStore, ObjectMapper objectMapper) {
        this.properties = properties;
        this.stockRepository = stockRepository;
        this.candleStore = candleStore;
        this.quoteStore = quoteStore;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 2_000)
    public void maintainConnection() {
        if (!properties.isLiveFeedEnabled() || !properties.hasCredentials()) {
            return;
        }
        if (!isMarketOpen()) {
            closeSocket();
            return;
        }
        if (socket == null && connecting.compareAndSet(false, true)) {
            connect();
        }
    }

    private boolean isMarketOpen() {
        ZonedDateTime now = ZonedDateTime.now(properties.getMarketZone());
        DayOfWeek day = now.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
                && !now.toLocalTime().isBefore(properties.getMarketOpen())
                && now.toLocalTime().isBefore(properties.getMarketClose());
    }

    private void connect() {
        List<Nifty50Constituent> stocks = stockRepository.findByActiveTrueOrderByRankAsc();
        stocksBySecurityId.clear();
        stocks.forEach(stock -> stocksBySecurityId.put(stock.getSecurityId(), stock));
        URI uri = URI.create(properties.getFeedUrl()
                + "?version=2&token=" + encode(properties.getAccessToken())
                + "&clientId=" + encode(properties.getClientId()) + "&authType=2");
        httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .buildAsync(uri, new FeedListener(stocks))
                .whenComplete((connected, error) -> {
                    connecting.set(false);
                    if (error != null) {
                        socket = null;
                        log.error("Could not connect to Dhan live feed ({})", error.getClass().getSimpleName());
                    }
                });
    }

    private void handle(DhanQuotePacketParser.ParsedQuote packet) {
        Nifty50Constituent stock = stocksBySecurityId.get(packet.securityId());
        if (stock == null) {
            return;
        }
        LocalDateTime tradedAt = LocalDateTime.ofInstant(packet.tradedAt(), properties.getMarketZone());
        LocalDateTime minute = tradedAt.truncatedTo(ChronoUnit.MINUTES);
        AtomicLong delta = new AtomicLong();
        volumes.compute(packet.securityId(), (key, previous) -> {
            LocalDate date = tradedAt.toLocalDate();
            if (previous == null || !previous.date().equals(date) || packet.dayVolume() < previous.volume()) {
                delta.set(0);
            } else {
                delta.set(packet.dayVolume() - previous.volume());
            }
            return new VolumeState(date, packet.dayVolume());
        });

        candleStore.applyLiveTick(stock.getId(), minute, packet.lastPrice(), delta.get());
        quoteStore.put(new LatestQuote(packet.securityId(), stock.getSymbol(), packet.lastPrice(),
                packet.lastQuantity(), packet.dayVolume(), packet.averagePrice(), packet.dayOpen(),
                packet.dayHigh(), packet.dayLow(), packet.dayClose(), packet.tradedAt()));
    }

    private String subscriptionMessage(List<Nifty50Constituent> stocks) throws JacksonException {
        List<Map<String, String>> instruments = stocks.stream()
                .map(stock -> Map.of("ExchangeSegment", stock.getExchangeSegment(),
                        "SecurityId", stock.getSecurityId()))
                .toList();
        return objectMapper.writeValueAsString(Map.of(
                "RequestCode", QUOTE_SUBSCRIBE_CODE,
                "InstrumentCount", instruments.size(),
                "InstrumentList", instruments));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @PreDestroy
    public void closeSocket() {
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "Market closed");
        }
    }

    public void credentialsChanged() {
        closeSocket();
        connecting.set(false);
    }

    private record VolumeState(LocalDate date, long volume) { }

    private final class FeedListener implements WebSocket.Listener {
        private final List<Nifty50Constituent> stocks;
        private final ByteArrayOutputStream fragments = new ByteArrayOutputStream();

        private FeedListener(List<Nifty50Constituent> stocks) {
            this.stocks = stocks;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            try {
                webSocket.sendText(subscriptionMessage(stocks), true);
                log.info("Connected to Dhan live feed and subscribed to {} stocks", stocks.size());
            } catch (JacksonException exception) {
                log.error("Could not create Dhan subscription", exception);
                webSocket.abort();
                socket = null;
            }
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            byte[] chunk = new byte[data.remaining()];
            data.get(chunk);
            synchronized (fragments) {
                fragments.writeBytes(chunk);
                if (last) {
                    byte[] message = fragments.toByteArray();
                    fragments.reset();
                    DhanQuotePacketParser.parse(message).forEach(DhanLiveFeedService.this::handle);
                }
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            socket = null;
            log.info("Dhan live feed closed ({}): {}", statusCode, reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            socket = null;
            log.error("Dhan live feed error: {}", error.getMessage());
        }
    }
}
