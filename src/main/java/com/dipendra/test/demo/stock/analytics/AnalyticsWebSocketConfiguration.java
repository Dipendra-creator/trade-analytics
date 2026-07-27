package com.dipendra.test.demo.stock.analytics;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class AnalyticsWebSocketConfiguration implements WebSocketConfigurer {
    private final AnalyticsWebSocketHandler handler;
    public AnalyticsWebSocketConfiguration(AnalyticsWebSocketHandler handler) { this.handler = handler; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/analytics").setAllowedOrigins("*");
    }
}
