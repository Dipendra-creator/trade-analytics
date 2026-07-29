package com.dipendra.test.demo.stock.analytics;

import com.dipendra.test.demo.stock.ai.AiAnalysisWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AnalyticsWebSocketConfiguration implements WebSocketConfigurer {
    private final AnalyticsWebSocketHandler handler;
    private final AiAnalysisWebSocketHandler aiHandler;
    private final String allowedOrigin;
    public AnalyticsWebSocketConfiguration(AnalyticsWebSocketHandler handler, AiAnalysisWebSocketHandler aiHandler,
            @Value("${security.allowed-origin:https://nse.revvlabs.tech}") String allowedOrigin) {
        this.handler = handler;
        this.aiHandler = aiHandler;
        this.allowedOrigin = allowedOrigin;
    }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/analytics").setAllowedOrigins(allowedOrigin);
        registry.addHandler(aiHandler, "/ws/ai-analysis").setAllowedOrigins(allowedOrigin);
    }
}
