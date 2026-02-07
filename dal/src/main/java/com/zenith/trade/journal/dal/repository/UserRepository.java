package com.zenith.trade.journal.dal.repository;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import java.util.UUID;

public class UserRepository {

    private final SqlClient client;

    public UserRepository(SqlClient client) {
        this.client = client;
    }

    public Future<Void> saveUserSecret(String userId, String userSecret) {
        return client.preparedQuery("UPDATE users SET user_secret = $1 WHERE id = $2")
                .execute(Tuple.of(userSecret, UUID.fromString(userId)))
                .mapEmpty();
    }

    public Future<String> getUserSecret(String userId) {
        return client.preparedQuery("SELECT user_secret FROM users WHERE id = $1")
                .execute(Tuple.of(UUID.fromString(userId)))
                .map(rows -> {
                    if (rows.size() > 0) {
                        return rows.iterator().next().getString("user_secret");
                    } else {
                        return null;
                    }
                });
    }
}
