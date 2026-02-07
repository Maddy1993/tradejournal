package com.zenith.trade.journal.dal.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Broker {
    private Integer id;
    private String name;
    private Boolean apiEnabled;
}
