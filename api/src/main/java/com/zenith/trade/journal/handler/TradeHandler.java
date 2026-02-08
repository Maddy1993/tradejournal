package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.model.Trade;
import com.zenith.trade.journal.dal.repository.TradeRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class TradeHandler {

    private static final Logger logger = LoggerFactory.getLogger(TradeHandler.class);
    private final SnapTradeService snapTradeService;
    private final TradeRepository tradeRepo;
    private final UserRepository userRepo;

    public TradeHandler(SnapTradeService snapTradeService, TradeRepository tradeRepo, UserRepository userRepo) {
        this.snapTradeService = snapTradeService;
        this.tradeRepo = tradeRepo;
        this.userRepo = userRepo;
    }

    /**
     * GET
     * /api/trades?userId={uuid}&symbol={symbol}&action={action}&startDate={ISO}&endDate={ISO}&limit={number}
     * Returns list of trades with optional filters
     */
    public void getTrades(RoutingContext ctx) {
        String userIdStr = ctx.request().getParam("userId");
        String symbol = ctx.request().getParam("symbol");
        String action = ctx.request().getParam("action");
        String startDateStr = ctx.request().getParam("startDate");
        String endDateStr = ctx.request().getParam("endDate");
        String limitStr = ctx.request().getParam("limit");

        if (userIdStr == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        try {
            UUID userId = UUID.fromString(userIdStr);
            OffsetDateTime startDate = startDateStr != null ? OffsetDateTime.parse(startDateStr) : null;
            OffsetDateTime endDate = endDateStr != null ? OffsetDateTime.parse(endDateStr) : null;
            Integer limit = limitStr != null ? Integer.parseInt(limitStr) : 100;

            tradeRepo.findWithFilters(userId, symbol, action, startDate, endDate, limit)
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
        String userIdStr = ctx.request().getParam("userId");

        if (userIdStr == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        try {
            UUID userId = UUID.fromString(userIdStr);

            tradeRepo.getStats(userId)
                    .onSuccess(stats -> {
                        JsonObject result = new JsonObject()
                                .put("totalTrades", stats.getTotalTrades())
                                .put("uniqueSymbols", stats.getUniqueSymbols())
                                .put("totalBought", stats.getTotalBought())
                                .put("totalSold", stats.getTotalSold())
                                .put("totalFees", stats.getTotalFees())
                                .put("netAmount", stats.getTotalSold().subtract(stats.getTotalBought()));
                        ctx.json(result);
                    })
                    .onFailure(t -> {
                        logger.error("Failed to get trade stats", t);
                        ctx.fail(500, t);
                    });
        } catch (Exception e) {
            ctx.fail(400, new IllegalArgumentException("Invalid userId"));
        }
    }

    /**
     * POST /api/trades/sync
     * Fetches trades/activities from SnapTrade and saves to database
     * Body: {"userId": "email", "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD"}
     */
    public void syncTrades(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String userId = body.getString("userId");
        String startDate = body.getString("startDate");
        String endDate = body.getString("endDate");

        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        userRepo.getUserSecret(userId)
                .compose(secret -> {
                    if (secret == null) {
                        throw new RuntimeException("User not registered with SnapTrade");
                    }
                    // Fetch all activities (trades) from SnapTrade
                    return snapTradeService.getTradeActivities(userId, secret, startDate, endDate);
                })
                .compose(activities -> {
                    // Save each trade activity to database
                    java.util.List<io.vertx.core.Future<Trade>> saveFutures = new java.util.ArrayList<>();

                    for (com.konfigthis.client.model.UniversalActivity activity : activities) {
                        // Get account ID from activity
                        String activityAccountId = activity.getAccount() != null
                                ? activity.getAccount().getId().toString()
                                : null;

                        if (activityAccountId == null)
                            continue;

                        // Map activity to Trade model
                        Trade trade = Trade.builder()
                                .accountId(UUID.fromString(activityAccountId))
                                .symbol(activity.getSymbol() != null ? activity.getSymbol().getSymbol() : "UNKNOWN")
                                .tradeDate(activity.getTradeDate().toLocalDate())
                                .action(determineAction(activity.getType()))
                                .quantity(activity.getUnits() != null
                                        ? new java.math.BigDecimal(activity.getUnits().toString())
                                        : java.math.BigDecimal.ZERO)
                                .price(activity.getPrice() != null
                                        ? new java.math.BigDecimal(activity.getPrice().toString())
                                        : java.math.BigDecimal.ZERO)
                                .commission(activity.getCurrency() != null
                                        ? java.math.BigDecimal.ZERO // Fee data not in UniversalActivity
                                        : java.math.BigDecimal.ZERO)
                                .fees(java.math.BigDecimal.ZERO)
                                .notes("Imported from SnapTrade")
                                .build();

                        saveFutures.add(tradeRepo.save(trade));
                    }

                    return io.vertx.core.CompositeFuture.all(new java.util.ArrayList<>(saveFutures))
                            .map(new JsonObject()
                                    .put("status", "success")
                                    .put("trades_synced", saveFutures.size()));
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
        java.math.BigDecimal subtotal = trade.getQuantity().multiply(trade.getPrice());
        java.math.BigDecimal totalFees = trade.getCommission().add(trade.getFees());

        // For BUY trades, add fees; for SELL trades, subtract fees
        if (trade.getAction().contains("BUY")) {
            return subtotal.add(totalFees);
        } else {
            return subtotal.subtract(totalFees);
        }
    }

    /**
     * Map SnapTrade activity type to trade action
     */
    private String determineAction(String activityType) {
        if (activityType == null)
            return "UNKNOWN";

        switch (activityType.toUpperCase()) {
            case "BUY":
            case "DEPOSIT":
                return "BUY";
            case "SELL":
            case "WITHDRAWAL":
                return "SELL";
            case "BTO":
            case "BUY_TO_OPEN":
                return "BUY_TO_OPEN";
            case "STC":
            case "SELL_TO_CLOSE":
                return "SELL_TO_CLOSE";
            case "STO":
            case "SELL_TO_OPEN":
                return "SELL_TO_OPEN";
            case "BTC":
            case "BUY_TO_CLOSE":
                return "BUY_TO_CLOSE";
            default:
                return activityType;
        }
    }
}
