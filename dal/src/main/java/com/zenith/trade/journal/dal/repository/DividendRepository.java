package com.zenith.trade.journal.dal.repository;

import com.zenith.trade.journal.dal.model.Dividend;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import io.vertx.core.Future;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class DividendRepository {

    private final SqlClient client;

    public DividendRepository(SqlClient client) {
        this.client = client;
    }

    public Future<List<Dividend>> findAllByAccountId(UUID accountId) {
        return client.preparedQuery("SELECT * FROM dividends WHERE account_id = $1 ORDER BY pay_date DESC")
                .execute(Tuple.of(accountId))
                .map(this::mapToDividends);
    }

    public Future<List<Dividend>> findAllByUserId(UUID userId) {
        return client
                .preparedQuery(
                        "SELECT d.* FROM dividends d JOIN broker_accounts a ON d.account_id = a.id WHERE a.user_id = $1 ORDER BY d.pay_date DESC")
                .execute(Tuple.of(userId))
                .map(this::mapToDividends);
    }

    public Future<Dividend> save(Dividend dividend) {
        String sql = "INSERT INTO dividends (account_id, symbol, amount, pay_date, ex_date) " +
                "VALUES ($1, $2, $3, $4, $5) " +
                "ON CONFLICT (account_id, symbol, pay_date) DO UPDATE SET " +
                "amount = $3, ex_date = $5 " +
                "RETURNING id";
        return client.preparedQuery(sql)
                .execute(Tuple.of(dividend.getAccountId(), dividend.getSymbol(), dividend.getAmount(),
                        dividend.getPayDate(), dividend.getExDate()))
                .map(rows -> {
                    Row row = rows.iterator().next();
                    dividend.setId(row.getUUID("id"));
                    return dividend;
                });
    }

    private List<Dividend> mapToDividends(RowSet<Row> rows) {
        List<Dividend> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(Dividend.builder()
                    .id(row.getUUID("id"))
                    .accountId(row.getUUID("account_id"))
                    .symbol(row.getString("symbol"))
                    .amount(row.getBigDecimal("amount"))
                    .payDate(row.getLocalDate("pay_date"))
                    .exDate(row.getLocalDate("ex_date"))
                    .createdAt(row.getOffsetDateTime("created_at"))
                    .build());
        }
        return list;
    }
}
