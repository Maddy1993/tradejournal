package com.zenith.trade.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class BrokerAccount {
    private UUID id;
    private UUID userId;
    private Integer brokerId;
    private String accountNumber;
    private String accountName;
    private Boolean isManual;
    private OffsetDateTime createdAt;

    public BrokerAccount() {}

    public BrokerAccount(UUID id, UUID userId, Integer brokerId, String accountNumber, String accountName, Boolean isManual, OffsetDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.brokerId = brokerId;
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.isManual = isManual;
        this.createdAt = createdAt;
    }

    public static BrokerAccountBuilder builder() {
        return new BrokerAccountBuilder();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Integer getBrokerId() { return brokerId; }
    public void setBrokerId(Integer brokerId) { this.brokerId = brokerId; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public Boolean getIsManual() { return isManual; }
    public void setIsManual(Boolean isManual) { this.isManual = isManual; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public static class BrokerAccountBuilder {
        private UUID id;
        private UUID userId;
        private Integer brokerId;
        private String accountNumber;
        private String accountName;
        private Boolean isManual;
        private OffsetDateTime createdAt;

        BrokerAccountBuilder() {}

        public BrokerAccountBuilder id(UUID id) { this.id = id; return this; }
        public BrokerAccountBuilder userId(UUID userId) { this.userId = userId; return this; }
        public BrokerAccountBuilder brokerId(Integer brokerId) { this.brokerId = brokerId; return this; }
        public BrokerAccountBuilder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public BrokerAccountBuilder accountName(String accountName) { this.accountName = accountName; return this; }
        public BrokerAccountBuilder isManual(Boolean isManual) { this.isManual = isManual; return this; }
        public BrokerAccountBuilder createdAt(OffsetDateTime createdAt) { this.createdAt = createdAt; return this; }

        public BrokerAccount build() {
            return new BrokerAccount(id, userId, brokerId, accountNumber, accountName, isManual, createdAt);
        }
    }
}
