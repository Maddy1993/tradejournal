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

        snapTradeService.registerUser(userId)
                .compose(secret -> userRepo.saveUserSecret(userId, secret).map(secret))
                .onSuccess(secret -> ctx.json(new JsonObject().put("userSecret", secret)))
                .onFailure(t -> {
                    logger.error("Failed to register user", t);
                    ctx.fail(500, t);
                });
    }

    public void generateConnectionLink(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String userId = body.getString("userId");
        String userSecret = body.getString("userSecret");

        snapTradeService.generateConnectionLink(userId, userSecret)
                .onSuccess(link -> ctx.json(new JsonObject().put("redirectURI", link)))
                .onFailure(t -> {
                    logger.error("Failed to generate link", t);
                    ctx.fail(500, t);
                });
    }

    public void syncHoldings(RoutingContext ctx) {
        String userId = ctx.body().asJsonObject().getString("userId");

        userRepo.getUserSecret(userId)
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
                                            .userId(java.util.UUID.fromString(userId))
                                            .brokerId(6) // SnapTrade ID
                                            .accountNumber(
                                                    account.getId() != null ? account.getId().toString() : "UNKNOWN")
                                            .accountName(account.getName())
                                            .build();

                                    // Save account and then process its holdings
                                    io.vertx.core.Future<Void> accountProcessing = accountRepo.save(brokerAccount)
                                            .compose(savedAccount -> {
                                                // Step 3: Process positions for this account
                                                List<io.vertx.core.Future<Holding>> holdingFutures = new ArrayList<>();

                                                if (accountHoldings.getPositions() != null) {
                                                    for (Object posObj : accountHoldings.getPositions()) {
                                                        com.konfigthis.client.model.Position position = (com.konfigthis.client.model.Position) posObj;

                                                        // Extract symbol information
                                                        String symbol = position.getSymbol() != null &&
                                                                position.getSymbol().getSymbol() != null
                                                                        ? position.getSymbol().getSymbol().getSymbol()
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
                                                                .currentPrice(currentPrice != null ? currentPrice
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
                })
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

        holdingRepo.findAllByUserId(java.util.UUID.fromString(userId))
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
