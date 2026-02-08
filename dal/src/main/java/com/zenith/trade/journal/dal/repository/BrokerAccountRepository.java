package com.zenith.trade.journal.dal.repository;

import com.zenith.trade.journal.dal.model.BrokerAccount;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import io.vertx.core.Future;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class BrokerAccountRepository {

    private final SqlClient client;

    public BrokerAccountRepository(SqlClient client) {
        this.client = client;
    }

    public Future<List<BrokerAccount>> findAllByUserId(UUID userId) {
        return client.preparedQuery("SELECT * FROM broker_accounts WHERE user_id = $1")
                .execute(Tuple.of(userId))
                .map(this::mapTimeout);
    }

    public Future<BrokerAccount> save(BrokerAccount account) {
        String sql = "INSERT INTO broker_accounts (user_id, broker_id, account_number, account_name, is_manual) " +
                "VALUES ($1, $2, $3, $4, $5) " +
                "ON CONFLICT (user_id, broker_id, account_number) DO UPDATE SET " +
                "account_name = $4, is_manual = $5 " +
                "RETURNING id";
        return client.preparedQuery(sql)
                .execute(Tuple.of(account.getUserId(), account.getBrokerId(),
                        account.getAccountNumber(), account.getAccountName(),
                        account.getIsManual() != null ? account.getIsManual() : false))
                .map(rows -> {
                    Row row = rows.iterator().next();
                    account.setId(row.getUUID("id"));
                    return account;
                });
    }

    private List<BrokerAccount> mapTimeout(RowSet<Row> rows) {
        List<BrokerAccount> list = new ArrayList<>();
        for (Row row : rows) {
            list.add(BrokerAccount.builder()
                    .id(row.getUUID("id"))
                    .userId(row.getUUID("user_id"))
                    .brokerId(row.getInteger("broker_id"))
                    .accountNumber(row.getString("account_number"))
                    .accountName(row.getString("account_name"))
                    .isManual(row.getBoolean("is_manual"))
                    .createdAt(row.getOffsetDateTime("created_at"))
                    .build());
        }
        return list;
    }
}
