package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.repository.HoldingRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.UUID;

public class DashboardHandler {

    private static final Logger logger = LoggerFactory.getLogger(DashboardHandler.class);
    private final SnapTradeService snapTradeService;
    private final HoldingRepository holdingRepo;
    private final UserRepository userRepo;

    public DashboardHandler(SnapTradeService snapTradeService, HoldingRepository holdingRepo, UserRepository userRepo) {
        this.snapTradeService = snapTradeService;
        this.holdingRepo = holdingRepo;
        this.userRepo = userRepo;
    }

    public void getDashboardSummary(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");
        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        UUID userUuid = UUID.fromString(userId);

        // Fetch holdings from DB for Equity and Unrealized P&L
        Future<JsonObject> holdingsSummary = holdingRepo.findAllByUserId(userUuid)
                .map(holdings -> {
                    double totalEquity = holdings.stream()
                            .mapToDouble(h -> h.getMarketValue().doubleValue())
                            .sum();

                    double totalCost = holdings.stream()
                            .mapToDouble(h -> h.getAverageCost().doubleValue() * h.getQuantity().doubleValue())
                            .sum();

                    double unrealizedPL = totalEquity - totalCost;

                    return new JsonObject()
                            .put("totalEquity", totalEquity)
                            .put("unrealizedPL", unrealizedPL);
                });

        // Fetch Realized P&L from SnapTrade (YTD default)
        Future<JsonObject> performanceFuture = userRepo.getUserSecret(userId)
                .compose(secret -> {
                    if (secret == null)
                        return Future.succeededFuture(new JsonObject());
                    LocalDate end = LocalDate.now();
                    LocalDate start = end.minusYears(1); // Default to 1 year for now
                    return snapTradeService.getPerformanceCustomRange(userId, secret,
                            start.toString(), end.toString())
                            .map(perf -> new JsonObject(perf.toJson()));
                })
                .recover(t -> {
                    logger.error("Failed to fetch performance", t);
                    return Future.succeededFuture(new JsonObject());
                });

        Future.all(holdingsSummary, performanceFuture)
                .onSuccess(composite -> {
                    JsonObject holdingsData = composite.resultAt(0);
                    JsonObject performanceData = composite.resultAt(1);

                    // Extract realized P&L from performanceData if available
                    // Structure depends on API, for now putting raw

                    JsonObject response = new JsonObject()
                            .mergeIn(holdingsData)
                            .put("performance", performanceData);

                    ctx.json(response);
                })
                .onFailure(t -> {
                    logger.error("Failed to generate dashboard summary", t);
                    ctx.fail(500, t);
                });
    }
}
