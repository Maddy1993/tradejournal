package com.zenith.trade.journal.service;

import com.konfigthis.client.Configuration;
import com.konfigthis.client.Snaptrade;
import com.konfigthis.client.model.Account;
import com.konfigthis.client.model.AccountHoldingsAccount;
import com.konfigthis.client.model.Position;
import com.konfigthis.client.model.UserIDandSecret;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;

public class SnapTradeService {

    private final Snaptrade snapTrade;

    public SnapTradeService(Vertx vertx) {
        String clientId = System.getProperty("SNAPTRADE_CLIENT_ID");
        if (clientId == null)
            clientId = System.getenv("SNAPTRADE_CLIENT_ID");

        String consumerKey = System.getProperty("SNAPTRADE_CLIENT_SECRET");
        if (consumerKey == null)
            consumerKey = System.getenv("SNAPTRADE_CLIENT_SECRET");

        if (clientId == null || consumerKey == null) {
            throw new RuntimeException("SNAPTRADE_CLIENT_ID and SNAPTRADE_CLIENT_SECRET must be set");
        }

        System.out.println("SnapTradeService initialized with ClientID: " + clientId); // Debug log

        Configuration configuration = new Configuration();
        configuration.clientId = clientId;
        configuration.consumerKey = consumerKey;
        this.snapTrade = new Snaptrade(configuration);
    }

    public Future<String> registerUser(String userId) {
        return Future.future(promise -> {
            try {
                UserIDandSecret response = snapTrade.authentication.registerSnapTradeUser(userId).execute();
                promise.complete(response.getUserSecret());
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    public Future<String> generateConnectionLink(String userId, String userSecret) {
        return Future.future(promise -> {
            try {
                // loginSnapTradeUser returns an object containing the redirectURI
                Object response = snapTrade.authentication.loginSnapTradeUser(userId, userSecret).execute();
                // Depending on SDK generation, this might be a specific model or Map.
                // Assuming it has a method or we can map it.
                // Converting to JsonObject to safely extract field
                JsonObject json = JsonObject.mapFrom(response);
                if (json.containsKey("redirectURI")) {
                    promise.complete(json.getString("redirectURI"));
                } else if (json.containsKey("loginRedirectURI")) {
                    promise.complete(json.getString("loginRedirectURI"));
                } else {
                    // Fallback check if response itself is the string (unlikely)
                    promise.complete(json.toString());
                }
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    public Future<JsonArray> getAccounts(String userId, String userSecret) {
        return Future.future(promise -> {
            try {
                List<Account> accounts = snapTrade.accountInformation.listUserAccounts(userId, userSecret).execute();
                JsonArray result = new JsonArray();
                for (Account account : accounts) {
                    result.add(JsonObject.mapFrom(account));
                }
                promise.complete(result);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    public Future<JsonArray> getHoldings(String userId, String userSecret, String accountId) {
        return Future.future(promise -> {
            try {
                UUID accountUuid = UUID.fromString(accountId);
                // Returns AccountHoldingsAccount which contains the list of positions
                AccountHoldingsAccount accountHoldings = snapTrade.accountInformation
                        .getUserHoldings(accountUuid, userId, userSecret).execute();

                JsonArray result = new JsonArray();
                List<Position> positions = accountHoldings.getPositions();
                if (positions != null) {
                    for (Position position : positions) {
                        result.add(JsonObject.mapFrom(position));
                    }
                }
                promise.complete(result);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }
}
