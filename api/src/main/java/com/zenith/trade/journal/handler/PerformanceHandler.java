package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceHandler {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceHandler.class);
    private final SnapTradeService snapTradeService;
    private final UserRepository userRepo;

    public PerformanceHandler(SnapTradeService snapTradeService, UserRepository userRepo) {
        this.snapTradeService = snapTradeService;
        this.userRepo = userRepo;
    }

    /**
     * GET
     * /api/performance?userId={email}&startDate={YYYY-MM-DD}&endDate={YYYY-MM-DD}
     * Fetches performance data (P&L) from SnapTrade
     */
    public void getPerformance(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");
        String startDate = ctx.request().getParam("startDate");
        String endDate = ctx.request().getParam("endDate");

        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        userRepo.getUserSecret(userId)
                .compose(secret -> {
                    if (secret == null) {
                        throw new RuntimeException("User not registered with SnapTrade");
                    }
                    return snapTradeService.getPerformanceCustomRange(userId, secret, startDate, endDate);
                })
                .onSuccess(performance -> {
                    // Convert entire PerformanceCustom object to JSON
                    JsonObject result = new JsonObject(performance.toJson());
                    ctx.json(result);
                })
                .onFailure(t -> {
                    logger.error("Failed to fetch performance data", t);
                    ctx.fail(500, t);
                });
    }
}
