package com.zenith.trade.journal.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerAccount {
    private UUID id;
    private UUID userId;
    private Integer brokerId;
    private String accountNumber;
    private String accountName;
    private Boolean isManual;
    private OffsetDateTime createdAt;
}
