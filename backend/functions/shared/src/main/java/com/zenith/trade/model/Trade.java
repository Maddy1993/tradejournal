package com.zenith.trade.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Trade {
    private UUID id;
    private UUID accountId;
    private String symbol;
    private java.time.LocalDate tradeDate;
    private String action; // BUY, SELL, BUY_TO_OPEN, SELL_TO_CLOSE, etc.
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal commission;
    private BigDecimal fees;
    private UUID strategyGroupId; // For grouping option legs
    private String notes;
    private OffsetDateTime createdAt;
    private BigDecimal realizedPl;
    private String tradeHash; // Hash for deduplication

    public Trade() {}

    public Trade(UUID id, UUID accountId, String symbol, java.time.LocalDate tradeDate, String action, BigDecimal quantity, BigDecimal price, BigDecimal commission, BigDecimal fees, UUID strategyGroupId, String notes, OffsetDateTime createdAt, BigDecimal realizedPl, String tradeHash) {
        this.id = id;
        this.accountId = accountId;
        this.symbol = symbol;
        this.tradeDate = tradeDate;
        this.action = action;
        this.quantity = quantity;
        this.price = price;
        this.commission = commission;
        this.fees = fees;
        this.strategyGroupId = strategyGroupId;
        this.notes = notes;
        this.createdAt = createdAt;
        this.realizedPl = realizedPl;
        this.tradeHash = tradeHash;
    }

    public static TradeBuilder builder() {
        return new TradeBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public java.time.LocalDate getTradeDate() { return tradeDate; }
    public void setTradeDate(java.time.LocalDate tradeDate) { this.tradeDate = tradeDate; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public BigDecimal getCommission() { return commission; }
    public void setCommission(BigDecimal commission) { this.commission = commission; }
    public BigDecimal getFees() { return fees; }
    public void setFees(BigDecimal fees) { this.fees = fees; }
    public UUID getStrategyGroupId() { return strategyGroupId; }
    public void setStrategyGroupId(UUID strategyGroupId) { this.strategyGroupId = strategyGroupId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public BigDecimal getRealizedPl() { return realizedPl; }
    public void setRealizedPl(BigDecimal realizedPl) { this.realizedPl = realizedPl; }
    public String getTradeHash() { return tradeHash; }
    public void setTradeHash(String tradeHash) { this.tradeHash = tradeHash; }

    public static class TradeBuilder {
        private UUID id;
        private UUID accountId;
        private String symbol;
        private java.time.LocalDate tradeDate;
        private String action;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal commission;
        private BigDecimal fees;
        private UUID strategyGroupId;
        private String notes;
        private OffsetDateTime createdAt;
        private BigDecimal realizedPl;
        private String tradeHash;

        TradeBuilder() {}

        public TradeBuilder id(UUID id) { this.id = id; return this; }
        public TradeBuilder accountId(UUID accountId) { this.accountId = accountId; return this; }
        public TradeBuilder symbol(String symbol) { this.symbol = symbol; return this; }
        public TradeBuilder tradeDate(java.time.LocalDate tradeDate) { this.tradeDate = tradeDate; return this; }
        public TradeBuilder action(String action) { this.action = action; return this; }
        public TradeBuilder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public TradeBuilder price(BigDecimal price) { this.price = price; return this; }
        public TradeBuilder commission(BigDecimal commission) { this.commission = commission; return this; }
        public TradeBuilder fees(BigDecimal fees) { this.fees = fees; return this; }
        public TradeBuilder strategyGroupId(UUID strategyGroupId) { this.strategyGroupId = strategyGroupId; return this; }
        public TradeBuilder notes(String notes) { this.notes = notes; return this; }
        public TradeBuilder createdAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }
        public TradeBuilder realizedPl(BigDecimal realizedPl) { this.realizedPl = realizedPl; return this; }
        public TradeBuilder tradeHash(String tradeHash) { this.tradeHash = tradeHash; return this; }

        public Trade build() {
            return new Trade(id, accountId, symbol, tradeDate, action, quantity, price, commission, fees, strategyGroupId, notes, createdAt, realizedPl, tradeHash);
        }
    }
}
