package com.dipendra.test.demo.stock.ai;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.ObjectMapper;

@Component
public class AiAnalysisWebSocketHandler extends TextWebSocketHandler {
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final AiTradeAnalysisService analysis;
    private final ObjectMapper objectMapper;

    public AiAnalysisWebSocketHandler(AiTradeAnalysisService analysis, ObjectMapper objectMapper) {
        this.analysis = analysis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        analysis.latest().ifPresent(snapshot -> send(session, snapshot));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Scheduled(fixedRate = 1_000, initialDelay = 5_000)
    public void publish() {
        if (sessions.isEmpty()) return;
        analysis.latest().ifPresent(snapshot -> {
            String json = objectMapper.writeValueAsString(snapshot);
            sessions.removeIf(session -> !send(session, json));
        });
    }

    private boolean send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) return false;
        try {
            String json = payload instanceof String text ? text : objectMapper.writeValueAsString(payload);
            synchronized (session) { session.sendMessage(new TextMessage(json)); }
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}
