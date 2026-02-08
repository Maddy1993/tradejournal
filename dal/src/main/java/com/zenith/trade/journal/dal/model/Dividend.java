package com.zenith.trade.journal.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dividend {
    private UUID id;
    private UUID accountId;
    private String symbol;
    private BigDecimal amount;
    private LocalDate payDate;
    private LocalDate exDate;
    private OffsetDateTime createdAt;
}
