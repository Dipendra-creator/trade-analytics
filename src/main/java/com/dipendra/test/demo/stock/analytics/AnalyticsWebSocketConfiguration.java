package com.dipendra.test.demo.stock.analytics;

import com.dipendra.test.demo.stock.ai.AiAnalysisWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AnalyticsWebSocketConfiguration implements WebSocketConfigurer {
    private final AnalyticsWebSocketHandler handler;
    private final AiAnalysisWebSocketHandler aiHandler;
    public AnalyticsWebSocketConfiguration(AnalyticsWebSocketHandler handler, AiAnalysisWebSocketHandler aiHandler) {
        this.handler = handler;
        this.aiHandler = aiHandler;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/analytics").setAllowedOrigins("*");
        registry.addHandler(aiHandler, "/ws/ai-analysis").setAllowedOrigins("*");
    }
}
