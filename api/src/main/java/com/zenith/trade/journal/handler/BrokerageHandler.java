package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.model.BrokerAccount;
import com.zenith.trade.journal.dal.model.Holding;
import com.zenith.trade.journal.dal.repository.BrokerAccountRepository;
import com.zenith.trade.journal.dal.repository.HoldingRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BrokerageHandler {

    private static final Logger logger = LoggerFactory.getLogger(BrokerageHandler.class);
    private final SnapTradeService snapTradeService;
    private final BrokerAccountRepository accountRepo;
    private final HoldingRepository holdingRepo;
    private final UserRepository userRepo;

    public BrokerageHandler(SnapTradeService snapTradeService, BrokerAccountRepository accountRepo,
            HoldingRepository holdingRepo, UserRepository userRepo) {
        this.snapTradeService = snapTradeService;
        this.accountRepo = accountRepo;
        this.holdingRepo = holdingRepo;
        this.userRepo = userRepo;
    }

    public void registerUser(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String userId = body.getString("userId");
        logger.info("Registering user: {}", userId);

        snapTradeService.registerUser(userId)
                .compose(secret -> {
                    logger.info("Got secret from SnapTrade for user {}: {}", userId, secret);
                    return userRepo.saveUserSecret(userId, secret).map(secret);
                })
                .onSuccess(secret -> {
                    logger.info("Successfully registered user {}", userId);
                    ctx.json(new JsonObject().put("userSecret", secret));
                })
                .onFailure(t -> {
                    logger.error("Failed to register user " + userId, t);
                    ctx.fail(500, t);
                });
    }

    public void generateConnectionLink(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String userId = body.getString("userId");
        String userSecret = body.getString("userSecret");

        snapTradeService.generateConnectionLink(userId, userSecret)
                .onFailure(err -> {
                    // If we get a 401 (Invalid userID or userSecret), try to sync the user
                    if (err.getMessage() != null && err.getMessage().contains("401")) {
                        logger.warn("Got 401 error for user {}, attempting to sync with SnapTrade", userId);

                        // Try to ensure user exists in SnapTrade and get/update their secret
                        snapTradeService.ensureUserExistsInSnapTrade(userId)
                                .compose(syncedSecret -> {
                                    // Retry connection with synced credentials
                                    return snapTradeService.generateConnectionLink(userId, syncedSecret);
                                })
                                .onSuccess(link -> ctx.json(new JsonObject().put("redirectURI", link)))
                                .onFailure(syncErr -> {
                                    logger.error("Failed to sync user and generate link", syncErr);
                                    ctx.fail(500, syncErr);
                                });
                    } else {
                        logger.error("Failed to generate link", err);
                        ctx.fail(500, err);
                    }
                })
                .onSuccess(link -> ctx.json(new JsonObject().put("redirectURI", link)));
    }

    /**
     * Handle OAuth callback after user connects broker.
     * SnapTrade redirects to this endpoint after successful authentication.
     * We fetch the connected accounts and persist them to database.
     */
    public void handleOAuthCallback(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");

        if (userId == null || userId.isEmpty()) {
            logger.error("No userId in callback");
            ctx.response()
                    .putHeader("Location", "http://localhost:3000/settings?error=missing_user")
                    .setStatusCode(302)
                    .end();
            return;
        }

        logger.info("OAuth callback for user: {}", userId);

        // Get user UUID and secret
        userRepo.getUserByEmail(userId)
                .compose(userUuid -> userRepo.getUserSecret(userId)
                        .map(userSecret -> new io.vertx.core.json.JsonObject()
                                .put("uuid", userUuid.toString())
                                .put("secret", userSecret)))
                .compose(userInfo -> {
                    String userSecret = userInfo.getString("secret");
                    java.util.UUID userUuid = java.util.UUID.fromString(userInfo.getString("uuid"));

                    if (userSecret == null) {
                        throw new RuntimeException("User not registered with SnapTrade");
                    }

                    // Fetch accounts from SnapTrade
                    return snapTradeService.getAccounts(userId, userSecret)
                            .compose(accountsJson -> {
                                logger.info("Found {} accounts for user {}", accountsJson.size(), userId);

                                // Save accounts to database
                                List<io.vertx.core.Future<BrokerAccount>> saveFutures = new ArrayList<>();

                                for (int i = 0; i < accountsJson.size(); i++) {
                                    io.vertx.core.json.JsonObject accountJson = accountsJson.getJsonObject(i);

                                    BrokerAccount account = BrokerAccount.builder()
                                            .userId(userUuid)
                                            .brokerId(6) // SnapTrade broker ID
                                            .accountNumber(accountJson.getString("id")) // SnapTrade account ID
                                            .accountName(accountJson.getString("name"))
                                            .isManual(false)
                                            .build();

                                    saveFutures.add(accountRepo.save(account));
                                }

                                return io.vertx.core.Future.all(saveFutures);
                            });
                })
                .onSuccess(v -> {
                    logger.info("Successfully saved broker accounts for user {}", userId);
                    // Redirect to frontend with success
                    ctx.response()
                            .putHeader("Location", "http://localhost:3000/settings?connection=success")
                            .setStatusCode(302)
                            .end();
                })
                .onFailure(err -> {
                    logger.error("Failed to handle OAuth callback", err);
                    ctx.response()
                            .putHeader("Location", "http://localhost:3000/settings?error=" + err.getMessage())
                            .setStatusCode(302)
                            .end();
                });
    }

    public void syncHoldings(RoutingContext ctx) {
        String userId = ctx.body().asJsonObject().getString("userId");
        logger.info("Starting holdings sync for user {}", userId);

        // Step 0: Get the user's UUID from their email
        userRepo.getUserIdByEmail(userId)
                .compose(userUuid -> userRepo.getUserSecret(userId)
                        .compose(secret -> {
                            if (secret == null) {
                                throw new RuntimeException("User not registered with SnapTrade");
                            }
                            // Step 1: Fetch all holdings from SnapTrade
                            return snapTradeService.getHoldings(userId, secret)
                                    .compose(accountHoldingsList -> {
                                        // Step 2: Process each account's holdings
                                        List<io.vertx.core.Future<Void>> accountFutures = new ArrayList<>();

                                        for (com.konfigthis.client.model.AccountHoldings accountHoldings : accountHoldingsList) {
                                            // Save account info first
                                            com.konfigthis.client.model.SnapTradeHoldingsAccount account = accountHoldings
                                                    .getAccount();

                                            BrokerAccount brokerAccount = BrokerAccount.builder()
                                                    .userId(userUuid) // Use the UUID not the email
                                                    .brokerId(6) // SnapTrade ID
                                                    .accountNumber(
                                                            account.getId() != null ? account.getId().toString()
                                                                    : "UNKNOWN")
                                                    .accountName(account.getName())
                                                    .build();

                                            // Save account and then process its holdings
                                            io.vertx.core.Future<Void> accountProcessing = accountRepo
                                                    .save(brokerAccount)
                                                    .compose(savedAccount -> {
                                                        // Step 3: Process positions for this account
                                                        List<io.vertx.core.Future<Holding>> holdingFutures = new ArrayList<>();

                                                        if (accountHoldings.getPositions() != null) {
                                                            for (Object posObj : accountHoldings.getPositions()) {
                                                                com.konfigthis.client.model.Position position = (com.konfigthis.client.model.Position) posObj;

                                                                // Extract symbol information
                                                                String symbol = position.getSymbol() != null &&
                                                                        position.getSymbol().getSymbol() != null
                                                                                ? position.getSymbol().getSymbol()
                                                                                        .getSymbol()
                                                                                : "UNKNOWN";

                                                                // Parse numeric values safely
                                                                java.math.BigDecimal quantity = parseDecimal(
                                                                        position.getUnits());
                                                                java.math.BigDecimal averageCost = parseDecimal(
                                                                        position.getAveragePurchasePrice());
                                                                java.math.BigDecimal currentPrice = parseDecimal(
                                                                        position.getPrice());
                                                                java.math.BigDecimal marketValue = quantity != null
                                                                        && currentPrice != null
                                                                                ? quantity.multiply(currentPrice)
                                                                                : java.math.BigDecimal.ZERO;

                                                                Holding holding = Holding.builder()
                                                                        .accountId(savedAccount.getId())
                                                                        .symbol(symbol)
                                                                        .quantity(quantity != null ? quantity
                                                                                : java.math.BigDecimal.ZERO)
                                                                        .averageCost(averageCost != null ? averageCost
                                                                                : java.math.BigDecimal.ZERO)
                                                                        .currentPrice(
                                                                                currentPrice != null ? currentPrice
                                                                                        : java.math.BigDecimal.ZERO)
                                                                        .marketValue(marketValue)
                                                                        .build();

                                                                holdingFutures.add(holdingRepo.save(holding));
                                                            }
                                                        }

                                                        // Wait for all holdings of this account to be saved
                                                        return io.vertx.core.CompositeFuture
                                                                .all(new ArrayList<>(holdingFutures))
                                                                .mapEmpty();
                                                    });

                                            accountFutures.add(accountProcessing);
                                        }

                                        // Step 4: Wait for all accounts to be processed
                                        return io.vertx.core.CompositeFuture.all(new ArrayList<>(accountFutures))
                                                .map(new JsonObject()
                                                        .put("status", "success")
                                                        .put("accounts_synced", accountHoldingsList.size()));
                                    });
                        }))
                .onSuccess(res -> ctx.json(res))
                .onFailure(t -> {
                    logger.error("Sync failed", t);
                    ctx.fail(500, t);
                });
    }

    public void getHoldings(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");
        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        userRepo.getUserIdByEmail(userId)
                .compose(userUuid -> holdingRepo.findAllByUserId(userUuid))
                .onSuccess(holdings -> {
                    io.vertx.core.json.JsonArray result = new io.vertx.core.json.JsonArray();
                    holdings.forEach(h -> {
                        JsonObject holdingJson = new JsonObject()
                                .put("symbol", h.getSymbol())
                                .put("quantity", h.getQuantity())
                                .put("averageCost", h.getAverageCost())
                                .put("currentPrice", h.getCurrentPrice())
                                .put("marketValue", h.getMarketValue());
                        result.add(holdingJson);
                    });
                    ctx.json(result);
                })
                .onFailure(t -> {
                    logger.error("Failed to get holdings", t);
                    ctx.fail(500, t);
                });
    }

    public void getAccounts(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");
        String userSecret = ctx.request().getParam("userSecret");

        if (userId == null || userSecret == null) {
            ctx.fail(400, new IllegalArgumentException("userId and userSecret are required"));
            return;
        }

        snapTradeService.getAccounts(userId, userSecret)
                .onSuccess(accounts -> ctx.json(accounts))
                .onFailure(t -> {
                    logger.error("Failed to get accounts", t);
                    ctx.fail(500, t);
                });
    }

    /**
     * Helper method to safely parse decimal values from SnapTrade API responses
     */
    private java.math.BigDecimal parseDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return new java.math.BigDecimal(value.toString());
            }
            if (value instanceof String) {
                return new java.math.BigDecimal((String) value);
            }
            return null;
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse decimal value: {}", value, e);
            return null;
        }
    }
}
