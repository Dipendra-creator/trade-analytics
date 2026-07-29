package com.dipendra.test.demo.stock.paper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramTradeNotifier {
    private static final Logger log = LoggerFactory.getLogger(TelegramTradeNotifier.class);
    private final String endpoint;
    private final String chatId;
    private final HttpClient client;

    public TelegramTradeNotifier(@Value("${telegram.bot-token:}") String token,
            @Value("${telegram.chat-id:}") String chatId) {
        this.endpoint = token == null || token.isBlank() ? "" : "https://api.telegram.org/bot" + token + "/sendMessage";
        this.chatId = chatId == null ? "" : chatId.trim();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public void opened(PaperTrade trade) {
        String icon = "LONG".equals(trade.getSide()) ? "🟢" : "🔴";
        send("🚀 <b>NEW NIFTY 50 TRADE</b>\n\n" + icon + " <b>" + html(trade.getSymbol()) + " · " + trade.getSide() + "</b>\n"
                + "💰 <b>Entry:</b> " + price(trade.getEntryPrice()) + "\n"
                + "🛑 <b>Stop:</b> " + price(trade.getStopPrice()) + "\n"
                + "🎯 <b>Target:</b> " + price(trade.getTargetPrice()) + "\n"
                + "📦 <b>Paper quantity:</b> " + trade.getQuantity() + "\n"
                + "🧠 <b>Confidence:</b> " + price(trade.getConfidence()) + "%\n\n"
                + "<i>Generated from live 5m/15m/60m momentum, breadth and Nifty contribution. Paper trade only.</i>\n\n"
                + "<a href=\"https://nse.revvlabs.tech/ai-analysis.html\">Open Live Trade Screen</a>");
    }

    public void closed(PaperTrade trade) {
        String icon = trade.getNetPnl() != null && trade.getNetPnl().signum() >= 0 ? "✅" : "❌";
        send(icon + " <b>TRADE CLOSED · " + html(trade.getExitReason()) + "</b>\n\n"
                + "<b>" + html(trade.getSymbol()) + " · " + trade.getSide() + "</b>\n"
                + "💰 <b>Entry:</b> " + price(trade.getEntryPrice()) + "\n"
                + "🏁 <b>Exit:</b> " + price(trade.getExitPrice()) + "\n"
                + "📊 <b>Net paper P&amp;L:</b> " + signed(trade.getNetPnl()) + "\n"
                + "⏱ <b>Held:</b> " + Duration.between(trade.getOpenedAt(), trade.getClosedAt()).toMinutes() + " min\n\n"
                + "<a href=\"https://nse.revvlabs.tech/ai-analysis.html\">Review Trade History</a>");
    }

    public void status(List<PaperTrade> trades, Map<String, Double> prices) {
        if (trades.isEmpty()) return;
        StringBuilder message = new StringBuilder("📡 <b>LIVE TRADE MONITOR · LATEST 3</b>\n\n");
        for (PaperTrade trade : trades.stream().limit(3).toList()) {
            BigDecimal current = trade.getExitPrice();
            if ("OPEN".equals(trade.getState()) && prices.get(trade.getSymbol()) != null) {
                current = BigDecimal.valueOf(prices.get(trade.getSymbol()));
            }
            BigDecimal pnl = trade.getNetPnl();
            if (pnl == null && current != null) {
                BigDecimal direction = "LONG".equals(trade.getSide()) ? BigDecimal.ONE : BigDecimal.ONE.negate();
                pnl = current.subtract(trade.getEntryPrice()).multiply(direction)
                        .multiply(BigDecimal.valueOf(trade.getQuantity()));
            }
            message.append("<b>").append(html(trade.getSymbol())).append(" · ").append(trade.getSide())
                    .append(" · ").append(trade.getState()).append("</b>\n")
                    .append("Entry ").append(price(trade.getEntryPrice())).append(" → Now ").append(price(current))
                    .append(" · P&amp;L ").append(signed(pnl)).append("\n\n");
        }
        message.append("<a href=\"https://nse.revvlabs.tech/ai-analysis.html\">Open Live Trade Screen</a>");
        send(message.toString());
    }

    private void send(String message) {
        if (endpoint.isBlank() || chatId.isBlank()) return;
        String body = "chat_id=" + encode(chatId) + "&parse_mode=HTML&disable_web_page_preview=true&text=" + encode(message);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, error) -> {
            if (error != null) log.warn("Telegram trade notification failed: {}", error.getMessage());
            else if (response.statusCode() >= 300) log.warn("Telegram trade notification returned HTTP {}", response.statusCode());
        });
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String price(BigDecimal value) { return value == null ? "--" : value.stripTrailingZeros().toPlainString(); }
    private static String signed(BigDecimal value) { return value == null ? "--" : (value.signum() >= 0 ? "+₹" : "-₹") + value.abs().setScale(2, java.math.RoundingMode.HALF_UP); }
    private static String html(String value) { return value == null ? "--" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
