package com.dipendra.test.demo.stock.config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dhan")
public class DhanProperties {
    private String clientId = "";
    private String accessToken = "";
    private String apiBaseUrl = "https://api.dhan.co/v2";
    private String feedUrl = "wss://api-feed.dhan.co";
    private boolean backfillEnabled = true;
    private boolean liveFeedEnabled = true;
    private LocalDateTime backfillFrom = LocalDateTime.of(2026, 7, 1, 9, 15);
    private LocalDateTime backfillTo;
    private ZoneId marketZone = ZoneId.of("Asia/Kolkata");
    private LocalTime marketOpen = LocalTime.of(9, 15);
    private LocalTime marketClose = LocalTime.of(15, 30);

    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank() && accessToken != null && !accessToken.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public String getFeedUrl() { return feedUrl; }
    public void setFeedUrl(String feedUrl) { this.feedUrl = feedUrl; }
    public boolean isBackfillEnabled() { return backfillEnabled; }
    public void setBackfillEnabled(boolean backfillEnabled) { this.backfillEnabled = backfillEnabled; }
    public boolean isLiveFeedEnabled() { return liveFeedEnabled; }
    public void setLiveFeedEnabled(boolean liveFeedEnabled) { this.liveFeedEnabled = liveFeedEnabled; }
    public LocalDateTime getBackfillFrom() { return backfillFrom; }
    public void setBackfillFrom(LocalDateTime backfillFrom) { this.backfillFrom = backfillFrom; }
    public LocalDateTime getBackfillTo() { return backfillTo; }
    public void setBackfillTo(LocalDateTime backfillTo) { this.backfillTo = backfillTo; }
    public ZoneId getMarketZone() { return marketZone; }
    public void setMarketZone(ZoneId marketZone) { this.marketZone = marketZone; }
    public LocalTime getMarketOpen() { return marketOpen; }
    public void setMarketOpen(LocalTime marketOpen) { this.marketOpen = marketOpen; }
    public LocalTime getMarketClose() { return marketClose; }
    public void setMarketClose(LocalTime marketClose) { this.marketClose = marketClose; }
}
