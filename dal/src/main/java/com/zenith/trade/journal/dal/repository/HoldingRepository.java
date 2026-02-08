package com.zenith.trade.journal.dal.repository;

import com.zenith.trade.journal.dal.model.Holding;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import io.vertx.core.Future;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class HoldingRepository {

    private final SqlClient client;

    public HoldingRepository(SqlClient client) {
        this.client = client;
    }

    public Future<List<Holding>> findAllByAccountId(UUID accountId) {
        return client.preparedQuery("SELECT * FROM holdings WHERE account_id = $1")
                .execute(Tuple.of(accountId))
                .map(this::mapToHoldings);
    }

    public Future<List<Holding>> findAllByUserId(UUID userId) {
        return client
                .preparedQuery(
                        "SELECT h.* FROM holdings h JOIN broker_accounts a ON h.account_id = a.id WHERE a.user_id = $1")
                .execute(Tuple.of(userId))
                .map(this::mapToHoldings);
    }

    public Future<Holding> save(Holding holding) {
        String sql = "INSERT INTO holdings (account_id, symbol, quantity, average_cost, current_price, market_value, last_updated) "
                +
                "VALUES ($1, $2, $3, $4, $5, $6, NOW()) " +
                "ON CONFLICT(account_id, symbol) DO UPDATE SET " +
                "quantity = $3, average_cost = $4, current_price = $5, market_value = $6, last_updated = NOW() " +
                "RETURNING id";
        return client.preparedQuery(sql)
                .execute(Tuple.of(holding.getAccountId(), holding.getSymbol(), holding.getQuantity(),
                        holding.getAverageCost(), holding.getCurrentPrice(), holding.getMarketValue()))
                .map(rows -> {
                    Row row = rows.iterator().next();
                    holding.setId(row.getUUID("id"));
                    return holding;
                });
    }

    private List<Holding> mapToHoldings(RowSet<Row> rows) {
        List<Holding> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(Holding.builder()
                    .id(row.getUUID("id"))
                    .accountId(row.getUUID("account_id"))
                    .symbol(row.getString("symbol"))
                    .quantity(row.getBigDecimal("quantity"))
                    .averageCost(row.getBigDecimal("average_cost"))
                    .currentPrice(row.getBigDecimal("current_price"))
                    .marketValue(row.getBigDecimal("market_value"))
                    .lastUpdated(row.getOffsetDateTime("last_updated"))
                    .build());
        }
        return list;
    }
}
