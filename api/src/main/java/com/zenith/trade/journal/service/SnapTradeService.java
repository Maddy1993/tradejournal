package com.zenith.trade.journal.service;

import com.konfigthis.client.Configuration;
import com.konfigthis.client.Snaptrade;
import com.konfigthis.client.model.Account;
import com.konfigthis.client.model.AccountHoldings;
import com.konfigthis.client.model.UserIDandSecret;
import com.konfigthis.client.model.PerformanceCustom;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

public class SnapTradeService {

    private static final Logger logger = LoggerFactory.getLogger(SnapTradeService.class);
    private final Snaptrade snapTrade;
    private final com.zenith.trade.journal.dal.repository.UserRepository userRepository;

    public SnapTradeService(Vertx vertx, com.zenith.trade.journal.dal.repository.UserRepository userRepository) {
        this.userRepository = userRepository;
        String clientId = System.getProperty("SNAPTRADE_CLIENT_ID");
        if (clientId == null)
            clientId = System.getenv("SNAPTRADE_CLIENT_ID");

        String consumerKey = System.getProperty("SNAPTRADE_CLIENT_SECRET");
        if (consumerKey == null)
            consumerKey = System.getenv("SNAPTRADE_CLIENT_SECRET");

        if (clientId == null || consumerKey == null) {
            throw new RuntimeException("SNAPTRADE_CLIENT_ID and SNAPTRADE_CLIENT_SECRET must be set");
        }

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
            } catch (com.konfigthis.client.ApiException e) {
                // SnapTrade returns 409 if user already exists
                // Instead of deleting (which would lose broker connections),
                // retrieve the existing userSecret from our database
                if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                    logger.warn("User {} already exists in SnapTrade, retrieving existing secret from database",
                            userId);
                    userRepository.getUserSecret(userId)
                            .onSuccess(existingSecret -> {
                                if (existingSecret != null) {
                                    logger.info("Retrieved existing userSecret for user {}", userId);
                                    promise.complete(existingSecret);
                                } else {
                                    logger.error("User exists in SnapTrade but no secret in database for {}", userId);
                                    promise.fail("User already exists but no secret found. Please contact support.");
                                }
                            })
                            .onFailure(dbError -> {
                                logger.error("Failed to retrieve existing userSecret", dbError);
                                promise.fail(dbError);
                            });
                } else {
                    logger.error("Failed to register user with SnapTrade: {}", e.getMessage(), e);
                    promise.fail(e);
                }
            } catch (Exception e) {
                logger.error("Unexpected error during user registration", e);
                promise.fail(e);
            }
        });
    }

    public Future<String> generateConnectionLink(String userId, String userSecret) {
        return Future.future(promise -> {
            try {
                // Set callback URL for after broker connection
                String callbackUrl = "http://localhost:8080/api/brokerage/callback?userId=" + userId;

                Object response = snapTrade.authentication
                        .loginSnapTradeUser(userId, userSecret)
                        .customRedirect(callbackUrl) // Where to redirect after connection
                        .immediateRedirect(true) // Redirect immediately after success
                        .execute();

                // SnapTrade SDK returns a Map object, cast it properly
                if (response instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> map = (java.util.Map<String, Object>) response;
                    String redirectUri = (String) map.get("redirectURI");

                    if (redirectUri != null) {
                        logger.info("Generated connection link with callback URL: {}", callbackUrl);
                        promise.complete(redirectUri);
                    } else {
                        promise.fail("No redirectURI in response. Available keys: " + map.keySet());
                    }
                } else {
                    promise.fail("Unexpected response type: " + response.getClass().getName());
                }
            } catch (Exception e) {
                logger.error("Failed to generate connection link", e);
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
                    JsonObject accountJson = new JsonObject()
                            .put("id", account.getId())
                            .put("name", account.getName())
                            .put("number", account.getNumber())
                            .put("institution_name", account.getInstitutionName());
                    result.add(accountJson);
                }
                promise.complete(result);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    public Future<List<AccountHoldings>> getHoldings(String userId, String userSecret) {
        return Future.future(promise -> {
            try {
                List<AccountHoldings> holdings = snapTrade.accountInformation.getAllUserHoldings(userId, userSecret)
                        .execute();
                promise.complete(holdings);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    public Future<PerformanceCustom> getPerformanceCustomRange(String userId, String userSecret, String startDateStr,
            String endDateStr) {
        return Future.future(promise -> {
            try {
                // Parse dates or use defaults (last 30 days if not provided)
                LocalDate startDate = startDateStr != null ? LocalDate.parse(startDateStr)
                        : LocalDate.now().minusDays(30);
                LocalDate endDate = endDateStr != null ? LocalDate.parse(endDateStr)
                        : LocalDate.now();

                PerformanceCustom performance = snapTrade.transactionsAndReporting
                        .getReportingCustomRange(startDate, endDate, userId, userSecret)
                        .execute();
                promise.complete(performance);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    public Future<List<com.konfigthis.client.model.UniversalActivity>> getDividendActivities(
            String userId, String userSecret, String startDate, String endDate) {
        return Future.future(promise -> {
            try {
                // Parse dates or use defaults (last year if not provided)
                LocalDate start = startDate != null ? LocalDate.parse(startDate)
                        : LocalDate.now().minusYears(1);
                LocalDate end = endDate != null ? LocalDate.parse(endDate)
                        : LocalDate.now();

                // Fetch activities filtered by type=DIVIDEND
                List<com.konfigthis.client.model.UniversalActivity> activities = snapTrade.transactionsAndReporting
                        .getActivities(userId, userSecret)
                        .startDate(start)
                        .endDate(end)
                        .type("DIVIDEND")
                        .execute();

                promise.complete(activities);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }

    /**
     * List all users registered with SnapTrade for this consumer.
     * This is useful for syncing users between our database and SnapTrade.
     */
    public Future<JsonArray> listAllSnapTradeUsers() {
        return Future.future(promise -> {
            try {
                List<String> userIds = snapTrade.authentication.listSnapTradeUsers().execute();
                JsonArray result = new JsonArray();
                for (String userId : userIds) {
                    result.add(userId);
                }
                logger.info("Found {} users in SnapTrade", userIds.size());
                promise.complete(result);
            } catch (Exception e) {
                logger.error("Failed to list SnapTrade users", e);
                promise.fail(e);
            }
        });
    }

    /**
     * Check if a user exists in SnapTrade by listing all users.
     * If the user doesn't exist, register them and update the database.
     */
    public Future<String> ensureUserExistsInSnapTrade(String userId) {
        return listAllSnapTradeUsers()
                .compose(snapTradeUsers -> {
                    // Check if user exists in SnapTrade
                    boolean userExists = false;
                    for (int i = 0; i < snapTradeUsers.size(); i++) {
                        if (userId.equals(snapTradeUsers.getString(i))) {
                            userExists = true;
                            break;
                        }
                    }

                    if (userExists) {
                        logger.info("User {} exists in SnapTrade, retrieving secret from database", userId);
                        // User exists, get their secret from database
                        return userRepository.getUserSecret(userId);
                    } else {
                        logger.warn("User {} does NOT exist in SnapTrade, registering now", userId);
                        // User doesn't exist in SnapTrade, register them
                        return registerUser(userId)
                                .compose(newSecret -> {
                                    // Update the database with the new secret
                                    return userRepository.saveUserSecret(userId, newSecret)
                                            .map(newSecret);
                                });
                    }
                });
    }

    public Future<List<com.konfigthis.client.model.UniversalActivity>> getTradeActivities(
            String userId, String userSecret, String startDate, String endDate) {
        return Future.future(promise -> {
            try {
                // Parse dates or use defaults (last year if not provided)
                LocalDate start = startDate != null ? LocalDate.parse(startDate)
                        : LocalDate.now().minusYears(1);
                LocalDate end = endDate != null ? LocalDate.parse(endDate)
                        : LocalDate.now();

                // Fetch all activities (will filter for trades in handler)
                // Note: SnapTrade doesn't have a specific "TRADE" type filter
                // We fetch all activities and filter for BUY/SELL types
                List<com.konfigthis.client.model.UniversalActivity> activities = snapTrade.transactionsAndReporting
                        .getActivities(userId, userSecret)
                        .startDate(start)
                        .endDate(end)
                        .execute();

                // Filter for trade-related activities only
                List<com.konfigthis.client.model.UniversalActivity> trades = activities.stream()
                        .filter(a -> {
                            String type = a.getType();
                            return type != null && (type.equalsIgnoreCase("BUY") ||
                                    type.equalsIgnoreCase("SELL") ||
                                    type.equalsIgnoreCase("BTO") ||
                                    type.equalsIgnoreCase("STC") ||
                                    type.equalsIgnoreCase("STO") ||
                                    type.equalsIgnoreCase("BTC") ||
                                    type.equalsIgnoreCase("BUY_TO_OPEN") ||
                                    type.equalsIgnoreCase("SELL_TO_CLOSE") ||
                                    type.equalsIgnoreCase("SELL_TO_OPEN") ||
                                    type.equalsIgnoreCase("BUY_TO_CLOSE"));
                        })
                        .collect(java.util.stream.Collectors.toList());

                promise.complete(trades);
            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }
}
