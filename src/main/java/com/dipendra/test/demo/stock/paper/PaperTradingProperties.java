package com.dipendra.test.demo.stock.paper;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "paper")
public class PaperTradingProperties {
    private boolean enabled = true;
    private BigDecimal startingCapital = new BigDecimal("1000000");
    private BigDecimal riskPerTrade = new BigDecimal("0.01");
    private int maxOpenPositions = 5;
    private BigDecimal maxDailyLoss = new BigDecimal("0.03");
    private int maxHoldMinutes = 30;
    private BigDecimal slippageBps = new BigDecimal("5");
    private BigDecimal transactionCostBpsPerSide = new BigDecimal("8");

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public BigDecimal getStartingCapital() { return startingCapital; }
    public void setStartingCapital(BigDecimal startingCapital) { this.startingCapital = startingCapital; }
    public BigDecimal getRiskPerTrade() { return riskPerTrade; }
    public void setRiskPerTrade(BigDecimal riskPerTrade) { this.riskPerTrade = riskPerTrade; }
    public int getMaxOpenPositions() { return maxOpenPositions; }
    public void setMaxOpenPositions(int maxOpenPositions) { this.maxOpenPositions = maxOpenPositions; }
    public BigDecimal getMaxDailyLoss() { return maxDailyLoss; }
    public void setMaxDailyLoss(BigDecimal maxDailyLoss) { this.maxDailyLoss = maxDailyLoss; }
    public int getMaxHoldMinutes() { return maxHoldMinutes; }
    public void setMaxHoldMinutes(int maxHoldMinutes) { this.maxHoldMinutes = maxHoldMinutes; }
    public BigDecimal getSlippageBps() { return slippageBps; }
    public void setSlippageBps(BigDecimal slippageBps) { this.slippageBps = slippageBps; }
    public BigDecimal getTransactionCostBpsPerSide() { return transactionCostBpsPerSide; }
    public void setTransactionCostBpsPerSide(BigDecimal transactionCostBpsPerSide) {
        this.transactionCostBpsPerSide = transactionCostBpsPerSide;
    }
}
