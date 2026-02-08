package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.model.Dividend;
import com.zenith.trade.journal.dal.repository.DividendRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DividendHandler {

    private static final Logger logger = LoggerFactory.getLogger(DividendHandler.class);
    private final SnapTradeService snapTradeService;
    private final DividendRepository dividendRepo;
    private final UserRepository userRepo;

    public DividendHandler(SnapTradeService snapTradeService, DividendRepository dividendRepo,
            UserRepository userRepo) {
        this.snapTradeService = snapTradeService;
        this.dividendRepo = dividendRepo;
        this.userRepo = userRepo;
    }

    /**
     * GET /api/dividends?userId={email}
     * Returns list of dividends from database
     */
    public void getDividends(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");

        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        dividendRepo.findAllByUserId(java.util.UUID.fromString(userId))
                .onSuccess(dividends -> {
                    JsonArray result = new JsonArray();
                    dividends.forEach(d -> {
                        JsonObject dividendJson = new JsonObject()
                                .put("id", d.getId().toString())
                                .put("symbol", d.getSymbol())
                                .put("amount", d.getAmount())
                                .put("payDate", d.getPayDate().toString())
                                .put("exDate", d.getExDate() != null ? d.getExDate().toString() : null);
                        result.add(dividendJson);
                    });
                    ctx.json(result);
                })
                .onFailure(t -> {
                    logger.error("Failed to get dividends", t);
                    ctx.fail(500, t);
                });
    }

    /**
     * POST /api/dividends/sync
     * Fetches dividend activities from SnapTrade and saves to database
     * Body: {"userId": "email", "startDate": "YYYY-MM-DD", "endDate": "YYYY-MM-DD"}
     */
    public void syncDividends(RoutingContext ctx) {
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
                    return snapTradeService.getDividendActivities(userId, secret, startDate, endDate);
                })
                .compose(activities -> {
                    // Save each dividend activity to database
                    java.util.List<io.vertx.core.Future<Dividend>> saveFutures = new java.util.ArrayList<>();

                    for (com.konfigthis.client.model.UniversalActivity activity : activities) {
                        // Get account ID from activity
                        String activityAccountId = activity.getAccount() != null
                                ? activity.getAccount().getId().toString()
                                : null;

                        if (activityAccountId == null)
                            continue;

                        // Extract dividend information
                        Dividend dividend = Dividend.builder()
                                .accountId(java.util.UUID.fromString(activityAccountId))
                                .symbol(activity.getSymbol() != null ? activity.getSymbol().getSymbol() : "UNKNOWN")
                                .amount(activity.getPrice() != null
                                        ? new java.math.BigDecimal(activity.getPrice().toString())
                                        : java.math.BigDecimal.ZERO)
                                .payDate(activity.getTradeDate().toLocalDate())
                                .exDate(null) // SnapTrade doesn't provide ex-date in activities
                                .build();

                        saveFutures.add(dividendRepo.save(dividend));
                    }

                    return io.vertx.core.CompositeFuture.all(new java.util.ArrayList<>(saveFutures))
                            .map(new JsonObject()
                                    .put("status", "success")
                                    .put("dividends_synced", saveFutures.size()));
                })
                .onSuccess(res -> ctx.json(res))
                .onFailure(t -> {
                    logger.error("Failed to sync dividends", t);
                    ctx.fail(500, t);
                });
    }
}
