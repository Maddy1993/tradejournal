package com.zenith.trade.journal.dal.repository;

import com.zenith.trade.journal.dal.model.Trade;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import io.vertx.core.Future;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class TradeRepository {

    private final SqlClient client;

    public TradeRepository(SqlClient client) {
        this.client = client;
    }

    /**
     * Find all trades for a specific account
     */
    public Future<List<Trade>> findAllByAccountId(UUID accountId) {
        return client.preparedQuery("SELECT * FROM trades WHERE account_id = $1 ORDER BY trade_date DESC")
                .execute(Tuple.of(accountId))
                .map(this::mapToTrades);
    }

    /**
     * Find all trades for a user (across all their accounts)
     */
    public Future<List<Trade>> findAllByUserId(UUID userId) {
        return client
                .preparedQuery(
                        "SELECT t.* FROM trades t " +
                                "JOIN broker_accounts a ON t.account_id = a.id " +
                                "WHERE a.user_id = $1 " +
                                "ORDER BY t.trade_date DESC")
                .execute(Tuple.of(userId))
                .map(this::mapToTrades);
    }

    /**
     * Find trades with filters
     * 
     * @param userId    User ID
     * @param symbol    Optional symbol filter
     * @param action    Optional action filter (BUY, SELL, etc.)
     * @param startDate Optional start date filter
     * @param endDate   Optional end date filter
     * @param limit     Optional limit (default 100)
     */
    public Future<List<Trade>> findWithFilters(UUID userId, String symbol, String action,
            OffsetDateTime startDate, OffsetDateTime endDate, Integer limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT t.* FROM trades t " +
                        "JOIN broker_accounts a ON t.account_id = a.id " +
                        "WHERE a.user_id = $1");

        List<Object> params = new ArrayList<>();
        params.add(userId);
        int paramIndex = 2;

        // Add optional filters
        if (symbol != null && !symbol.isEmpty()) {
            sql.append(" AND t.symbol = $").append(paramIndex++);
            params.add(symbol);
        }

        if (action != null && !action.isEmpty()) {
            sql.append(" AND t.action = $").append(paramIndex++);
            params.add(action);
        }

        if (startDate != null) {
            sql.append(" AND t.trade_date >= $").append(paramIndex++);
            params.add(startDate);
        }

        if (endDate != null) {
            sql.append(" AND t.trade_date <= $").append(paramIndex++);
            params.add(endDate);
        }

        sql.append(" ORDER BY t.trade_date DESC");

        // Add limit
        if (limit != null && limit > 0) {
            sql.append(" LIMIT $").append(paramIndex);
            params.add(limit);
        } else {
            sql.append(" LIMIT 100"); // Default limit
        }

        return client.preparedQuery(sql.toString())
                .execute(Tuple.from(params.toArray()))
                .map(this::mapToTrades);
    }

    /**
     * Save a new trade or update existing
     */
    public Future<Trade> save(Trade trade) {
        String sql = "INSERT INTO trades (account_id, symbol, trade_date, action, quantity, price, commission, fees, strategy_group_id, notes, realized_pl, trade_hash) "
                +
                "VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12) " +
                "ON CONFLICT (trade_hash) DO UPDATE SET " +
                "realized_pl = EXCLUDED.realized_pl, " +
                "notes = EXCLUDED.notes " +
                "RETURNING id, created_at";

        return client.preparedQuery(sql)
                .execute(Tuple.of(
                        trade.getAccountId(),
                        trade.getSymbol(),
                        trade.getTradeDate(),
                        trade.getAction(),
                        trade.getQuantity(),
                        trade.getPrice(),
                        trade.getCommission(),
                        trade.getFees(),
                        trade.getStrategyGroupId(),
                        trade.getNotes(),
                        trade.getRealizedPl(),
                        trade.getTradeHash()))
                .map(rows -> {
                    Row row = rows.iterator().next();
                    trade.setId(row.getUUID("id"));
                    trade.setCreatedAt(row.getOffsetDateTime("created_at"));
                    return trade;
                });
    }

    /**
     * Get trade statistics for a user
     */
    public Future<TradeStats> getStats(UUID userId) {
        String sql = "SELECT " +
                "COUNT(*) as total_trades, " +
                "COUNT(DISTINCT symbol) as unique_symbols, " +
                "SUM(CASE WHEN action IN ('BUY', 'BUY_TO_OPEN') THEN quantity * price ELSE 0 END) as total_bought, " +
                "SUM(CASE WHEN action IN ('SELL', 'SELL_TO_CLOSE') THEN quantity * price ELSE 0 END) as total_sold, " +
                "SUM(commission + fees) as total_fees " +
                "FROM trades t " +
                "JOIN broker_accounts a ON t.account_id = a.id " +
                "WHERE a.user_id = $1";

        return client.preparedQuery(sql)
                .execute(Tuple.of(userId))
                .map(rows -> {
                    Row row = rows.iterator().next();
                    return TradeStats.builder()
                            .totalTrades(row.getLong("total_trades"))
                            .uniqueSymbols(row.getLong("unique_symbols"))
                            .totalBought(row.getBigDecimal("total_bought"))
                            .totalSold(row.getBigDecimal("total_sold"))
                            .totalFees(row.getBigDecimal("total_fees"))
                            .build();
                });
    }

    /**
     * Update realized P&L for a trade
     */
    public Future<Void> updateRealizedPl(UUID tradeId, java.math.BigDecimal realizedPl) {
        return client.preparedQuery("UPDATE trades SET realized_pl = $1 WHERE id = $2")
                .execute(Tuple.of(realizedPl, tradeId))
                .mapEmpty();
    }

    /**
     * Map database rows to Trade objects
     */
    private List<Trade> mapToTrades(RowSet<Row> rows) {
        List<Trade> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(Trade.builder()
                    .id(row.getUUID("id"))
                    .accountId(row.getUUID("account_id"))
                    .symbol(row.getString("symbol"))
                    .tradeDate(row.getLocalDate("trade_date"))
                    .action(row.getString("action"))
                    .quantity(row.getBigDecimal("quantity"))
                    .price(row.getBigDecimal("price"))
                    .commission(row.getBigDecimal("commission"))
                    .fees(row.getBigDecimal("fees"))
                    .strategyGroupId(row.getUUID("strategy_group_id"))
                    .notes(row.getString("notes"))
                    .createdAt(row.getOffsetDateTime("created_at"))
                    .realizedPl(row.getBigDecimal("realized_pl"))
                    .build());
        }
        return list;
    }

    /**
     * Inner class for trade statistics
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TradeStats {
        private Long totalTrades;
        private Long uniqueSymbols;
        private java.math.BigDecimal totalBought;
        private java.math.BigDecimal totalSold;
        private java.math.BigDecimal totalFees;
    }
}
