package com.zenith.trade.journal.dal.repository;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

public class UserRepository {

    private final SqlClient client;

    public UserRepository(SqlClient client) {
        this.client = client;
    }

    public Future<Void> saveUserSecret(String userId, String userSecret) {
        // Insert or update user by email (userId is email)
        // Since schema has id as UUID auto-generated, we use email as lookup
        String query = "INSERT INTO users (email, user_secret) VALUES ($1, $2) " +
                "ON CONFLICT (email) DO UPDATE SET user_secret = $2";
        return client.preparedQuery(query)
                .execute(Tuple.of(userId, userSecret))
                .mapEmpty();
    }

    public Future<String> getUserSecret(String userId) {
        // Look up by email since userId is email
        return client.preparedQuery("SELECT user_secret FROM users WHERE email = $1")
                .execute(Tuple.of(userId))
                .map(rows -> {
                    if (rows.size() > 0) {
                        return rows.iterator().next().getString("user_secret");
                    } else {
                        return null;
                    }
                });
    }
}
