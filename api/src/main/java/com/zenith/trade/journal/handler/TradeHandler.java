package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.model.Trade;
import com.zenith.trade.journal.dal.repository.BrokerAccountRepository;
import com.zenith.trade.journal.dal.repository.TradeRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.UUID;

public class TradeHandler {

    private static final Logger logger = LoggerFactory.getLogger(TradeHandler.class);
    private final SnapTradeService snapTradeService;
    private final TradeRepository tradeRepo;
    private final UserRepository userRepo;
    private final BrokerAccountRepository brokerAccountRepo;
    private final com.zenith.trade.journal.service.RealizedPLService realizedPLService;

    public TradeHandler(SnapTradeService snapTradeService, TradeRepository tradeRepo, UserRepository userRepo,
            BrokerAccountRepository brokerAccountRepo,
            com.zenith.trade.journal.service.RealizedPLService realizedPLService) {
        this.snapTradeService = snapTradeService;
        this.tradeRepo = tradeRepo;
        this.userRepo = userRepo;
        this.brokerAccountRepo = brokerAccountRepo;
        this.realizedPLService = realizedPLService;
    }

    /**
     * GET
     * /api/trades?userId={uuid}&symbol={symbol}&action={action}&startDate={ISO}&endDate={ISO}&limit={number}
     * Returns list of trades with optional filters
     */
    public void getTrades(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");
        String symbol = ctx.request().getParam("symbol");
        String action = ctx.request().getParam("action");
        String startDateStr = ctx.request().getParam("startDate");
        String endDateStr = ctx.request().getParam("endDate");
        String limitStr = ctx.request().getParam("limit");

        if (userId == null || userId.trim().isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        try {
            OffsetDateTime startDate = startDateStr != null ? OffsetDateTime.parse(startDateStr) : null;
            OffsetDateTime endDate = endDateStr != null ? OffsetDateTime.parse(endDateStr) : null;
            Integer limit = limitStr != null ? Integer.parseInt(limitStr) : 100;

            // Convert email to UUID
            userRepo.getUserIdByEmail(userId)
                    .compose(userUuid -> tradeRepo.findWithFilters(userUuid, symbol, action, startDate, endDate, limit))
                    .onSuccess(trades -> {
                        JsonArray result = new JsonArray();
                        trades.forEach(t -> {
                            JsonObject tradeJson = new JsonObject()
                                    .put("id", t.getId().toString())
                                    .put("accountId", t.getAccountId().toString())
                                    .put("symbol", t.getSymbol())
                                    .put("tradeDate", t.getTradeDate().toString())
                                    .put("action", t.getAction())
                                    .put("quantity", t.getQuantity())
                                    .put("price", t.getPrice())
                                    .put("commission", t.getCommission())
                                    .put("fees", t.getFees())
                                    .put("totalCost", calculateTotalCost(t))
                                    .put("strategyGroupId",
                                            t.getStrategyGroupId() != null ? t.getStrategyGroupId().toString() : null)
                                    .put("notes", t.getNotes());
                            result.add(tradeJson);
                        });
                        ctx.json(result);
                    })
                    .onFailure(t -> {
                        logger.error("Failed to get trades", t);
                        ctx.fail(500, t);
                    });
        } catch (Exception e) {
            ctx.fail(400, new IllegalArgumentException("Invalid parameters: " + e.getMessage()));
        }
    }

    /**
     * GET /api/trades/stats?userId={uuid}
     * Returns trade statistics for a user
     */
    public void getTradeStats(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");

        if (userId == null || userId.trim().isEmpty()) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        // Convert email to UUID
        userRepo.getUserIdByEmail(userId)
                .compose(userUuid -> tradeRepo.getStats(userUuid))
                .onSuccess(stats -> {
                    // Handle null values from SQL query (when no trades exist)
                    java.math.BigDecimal totalBought = stats.getTotalBought() != null
                            ? stats.getTotalBought()
                            : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal totalSold = stats.getTotalSold() != null
                            ? stats.getTotalSold()
                            : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal totalFees = stats.getTotalFees() != null
                            ? stats.getTotalFees()
                            : java.math.BigDecimal.ZERO;

                    JsonObject result = new JsonObject()
                            .put("totalTrades", stats.getTotalTrades() != null ? stats.getTotalTrades() : 0)
                            .put("uniqueSymbols", stats.getUniqueSymbols() != null ? stats.getUniqueSymbols() : 0)
                            .put("totalBought", totalBought)
                            .put("totalSold", totalSold)
                            .put("totalFees", totalFees)
                            .put("netAmount", totalSold.subtract(totalBought));
                    ctx.json(result);
                })
                .onFailure(t -> {
                    logger.error("Failed to get trade stats", t);
                    ctx.fail(500, t);
                });
    }

    /**
     * POST /api/trades/sync
     * Fetches trades/activities from SnapTrade and saves to database
     * Body: {"userId": "email", "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD"}
     */
    public void syncTrades(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String userId = body.getString("userId");
        logger.info("Starting trades sync for user {}", userId);
        String startDate = body.getString("startDate");
        String endDate = body.getString("endDate");

        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        // First resolve user UUID from email
        userRepo.getUserIdByEmail(userId)
                .compose(userUuid -> {
                    // Then get user secret to query SnapTrade
                    return userRepo.getUserSecret(userId)
                            .compose(secret -> {
                                if (secret == null) {
                                    throw new RuntimeException("User not registered with SnapTrade");
                                }
                                // Fetch all activities (trades) from SnapTrade
                                return snapTradeService.getTradeActivities(userId, secret, startDate, endDate)
                                        .map(activities -> new Object[] { userUuid, activities });
                            });
                })
                .compose(data -> {
                    UUID userUuid = (UUID) data[0];
                    java.util.List<com.konfigthis.client.model.UniversalActivity> activities = (java.util.List<com.konfigthis.client.model.UniversalActivity>) data[1];

                    // Save each trade activity to database
                    java.util.List<io.vertx.core.Future<Trade>> saveFutures = new java.util.ArrayList<>();

                    for (com.konfigthis.client.model.UniversalActivity activity : activities) {
                        // Get account ID from activity (this is SnapTrade's account ID)
                        String snapTradeAccountId = activity.getAccount() != null
                                ? activity.getAccount().getId().toString()
                                : null;

                        if (snapTradeAccountId == null)
                            continue;

                        // Look up the broker_account in our database using account_number
                        io.vertx.core.Future<Trade> tradeFuture = brokerAccountRepo
                                .findByAccountNumber(userUuid, snapTradeAccountId)
                                .compose(brokerAccount -> {
                                    if (brokerAccount == null) {
                                        logger.warn("No broker account found for SnapTrade account ID: {}",
                                                snapTradeAccountId);
                                        return io.vertx.core.Future.succeededFuture(null);
                                    }

                                    // Map activity to Trade model using our broker_account.id
                                    Trade trade = Trade.builder()
                                            .accountId(brokerAccount.getId())
                                            .symbol(activity.getSymbol() != null ? activity.getSymbol().getSymbol()
                                                    : "UNKNOWN")
                                            .tradeDate(activity.getTradeDate().toLocalDate())
                                            .action(determineAction(activity.getType()))
                                            .quantity(activity.getUnits() != null
                                                    ? new java.math.BigDecimal(activity.getUnits().toString())
                                                    : java.math.BigDecimal.ZERO)
                                            .price(activity.getPrice() != null
                                                    ? new java.math.BigDecimal(activity.getPrice().toString())
                                                    : java.math.BigDecimal.ZERO)
                                            .commission(java.math.BigDecimal.ZERO) // Fee data not in UniversalActivity
                                            .fees(java.math.BigDecimal.ZERO)
                                            .notes("Imported from SnapTrade")
                                            .build();

                                    // Generate hash for deduplication
                                    String tradeHash = generateTradeHash(
                                            brokerAccount.getId(),
                                            trade.getSymbol(),
                                            trade.getTradeDate(),
                                            trade.getAction(),
                                            trade.getQuantity(),
                                            trade.getPrice(),
                                            activity.getTradeDate() // Include full timestamp
                                    );
                                    trade.setTradeHash(tradeHash);

                                    return tradeRepo.save(trade);
                                });

                        saveFutures.add(tradeFuture);
                    }

                    return io.vertx.core.CompositeFuture.all(new java.util.ArrayList<>(saveFutures))
                            .map(cf -> {
                                // Filter out nulls (skipped trades)
                                long savedCount = saveFutures.stream()
                                        .filter(f -> f.succeeded() && f.result() != null)
                                        .count();
                                return new JsonObject()
                                        .put("status", "success")
                                        .put("trades_synced", savedCount);
                            })
                            .compose(json -> {
                                // Better approach: Get all accounts for user and re-calc P&L for all of them
                                return brokerAccountRepo.findAllByUserId(userUuid)
                                        .compose(accounts -> {
                                            java.util.List<io.vertx.core.Future<Void>> calcFutures = new java.util.ArrayList<>();
                                            for (com.zenith.trade.journal.dal.model.BrokerAccount account : accounts) {
                                                io.vertx.core.Future<Void> f = tradeRepo
                                                        .findAllByAccountId(account.getId())
                                                        .compose(trades -> {
                                                            if (trades.isEmpty())
                                                                return io.vertx.core.Future.succeededFuture();

                                                            // Calculate P&L
                                                            java.util.List<Trade> calculatedTrades = realizedPLService
                                                                    .calculateRealizedPL(trades);

                                                            // Update trades with new P&L
                                                            java.util.List<io.vertx.core.Future<Void>> updateFutures = new java.util.ArrayList<>();
                                                            for (Trade t : calculatedTrades) {
                                                                // Only update if realizedPl changed or is set
                                                                // (optimization possible here)
                                                                if (t.getRealizedPl() != null) {
                                                                    updateFutures.add(tradeRepo.updateRealizedPl(
                                                                            t.getId(), t.getRealizedPl()));
                                                                }
                                                            }
                                                            return io.vertx.core.CompositeFuture
                                                                    .all(new java.util.ArrayList<>(updateFutures))
                                                                    .mapEmpty();
                                                        });
                                                calcFutures.add(f);
                                            }
                                            return io.vertx.core.CompositeFuture
                                                    .all(new java.util.ArrayList<>(calcFutures));
                                        })
                                        .map(json);
                            });
                })
                .onSuccess(res -> ctx.json(res))
                .onFailure(t -> {
                    logger.error("Failed to sync trades", t);
                    ctx.fail(500, t);
                });
    }

    /**
     * Calculate total cost of a trade (including commission and fees)
     */
    private java.math.BigDecimal calculateTotalCost(Trade trade) {
        java.math.BigDecimal baseAmount = trade.getPrice().multiply(trade.getQuantity());
        java.math.BigDecimal totalFees = trade.getCommission().add(trade.getFees());

        // For BUY orders, add fees to cost. For SELL orders, subtract fees from
        // proceeds
        if (trade.getAction().equals("BUY") || trade.getAction().contains("BUY")) {
            return baseAmount.add(totalFees);
        } else {
            return baseAmount.subtract(totalFees);
        }
    }

    /**
     * Map SnapTrade activity type to trade action
     */
    private String determineAction(String activityType) {
        if (activityType == null)
            return "UNKNOWN";

        activityType = activityType.toUpperCase();
        switch (activityType) {
            case "BUY":
            case "BUY_TO_OPEN":
            case "BTO":
                return "BUY";
            case "SELL":
            case "SELL_TO_CLOSE":
            case "STC":
                return "SELL";
            case "SELL_TO_OPEN":
            case "STO":
                return "SELL_TO_OPEN";
            case "BUY_TO_CLOSE":
            case "BTC":
                return "BUY_TO_CLOSE";
            default:
                return activityType;
        }
    }

    /**
     * Generate a unique hash for a trade to prevent duplicates
     * Uses SHA-256 hash of trade properties including timestamp
     */
    private String generateTradeHash(
            java.util.UUID accountId,
            String symbol,
            java.time.LocalDate tradeDate,
            String action,
            java.math.BigDecimal quantity,
            java.math.BigDecimal price,
            java.time.OffsetDateTime timestamp) {
        try {
            // Combine all trade properties into a single string
            String tradeData = String.format("%s|%s|%s|%s|%s|%s|%s",
                    accountId.toString(),
                    symbol,
                    tradeDate.toString(),
                    action,
                    quantity.toPlainString(),
                    price.toPlainString(),
                    timestamp.toString());

            // Generate SHA-256 hash
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(tradeData.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            logger.error("Failed to generate trade hash", e);
            // Fallback to simple concatenation if SHA-256 is not available
            return String.format("%s_%s_%s_%s_%s_%s_%s",
                    accountId, symbol, tradeDate, action, quantity, price, timestamp);
        }
    }
}
