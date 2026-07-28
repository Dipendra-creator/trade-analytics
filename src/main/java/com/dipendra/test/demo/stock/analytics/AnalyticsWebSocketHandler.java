package com.dipendra.test.demo.stock.analytics;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

@Component
public class AnalyticsWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsWebSocketHandler.class);
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final LiveAnalyticsService analytics;
    private final ObjectMapper objectMapper;

    public AnalyticsWebSocketHandler(LiveAnalyticsService analytics, ObjectMapper objectMapper) {
        this.analytics = analytics;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        MarketAnalyticsSnapshot snapshot = analytics.latest().orElse(null);
        if (snapshot != null) send(session, snapshot);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 1_000, initialDelay = 5_000)
    public void publish() {
        if (sessions.isEmpty()) return;
        try {
            MarketAnalyticsSnapshot snapshot = analytics.latest().orElse(null);
            if (snapshot == null) return;
            String json = objectMapper.writeValueAsString(snapshot);
            sessions.removeIf(session -> !send(session, json));
        } catch (RuntimeException exception) {
            log.warn("Could not calculate live analytics: {}", exception.getMessage());
        }
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        send(session, objectMapper.writeValueAsString(payload));
    }

    private boolean send(WebSocketSession session, String json) {
        if (!session.isOpen()) return false;
        try { synchronized (session) { session.sendMessage(new TextMessage(json)); } return true; }
        catch (IOException exception) { return false; }
    }
}
