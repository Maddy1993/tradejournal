package com.zenith.trade.journal.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
