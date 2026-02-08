package com.zenith.trade.journal.handler;

import com.zenith.trade.journal.dal.model.Dividend;
import com.zenith.trade.journal.dal.repository.BrokerAccountRepository;
import com.zenith.trade.journal.dal.repository.DividendRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.UUID;

public class DividendHandler {

    private static final Logger logger = LoggerFactory.getLogger(DividendHandler.class);
    private final SnapTradeService snapTradeService;
    private final DividendRepository dividendRepo;
    private final UserRepository userRepo;
    private final BrokerAccountRepository brokerAccountRepo;

    public DividendHandler(SnapTradeService snapTradeService, DividendRepository dividendRepo,
            UserRepository userRepo, BrokerAccountRepository brokerAccountRepo) {
        this.snapTradeService = snapTradeService;
        this.dividendRepo = dividendRepo;
        this.userRepo = userRepo;
        this.brokerAccountRepo = brokerAccountRepo;
    }

    /**
     * GET /api/dividends?userId={email}&startDate={ISO}&endDate={ISO}
     * Returns list of dividends
     */
    public void getDividends(RoutingContext ctx) {
        String userId = ctx.request().getParam("userId");
        String startDateStr = ctx.request().getParam("startDate");
        String endDateStr = ctx.request().getParam("endDate");

        if (userId == null) {
            ctx.fail(400, new IllegalArgumentException("userId is required"));
            return;
        }

        try {
            OffsetDateTime startDate = startDateStr != null ? OffsetDateTime.parse(startDateStr) : null;
            OffsetDateTime endDate = endDateStr != null ? OffsetDateTime.parse(endDateStr) : null;

            // Convert email to UUID
            userRepo.getUserIdByEmail(userId)
                    .compose(userUuid -> dividendRepo.findAllByUserId(userUuid))
                    .onSuccess(dividends -> {
                        JsonArray result = new JsonArray();
                        dividends.forEach(d -> {
                            JsonObject dividendJson = new JsonObject()
                                    .put("id", d.getId().toString())
                                    .put("accountId", d.getAccountId().toString())
                                    .put("symbol", d.getSymbol())
                                    .put("paymentDate", d.getPayDate().toString())
                                    .put("amount", d.getAmount());
                            result.add(dividendJson);
                        });
                        ctx.json(result);
                    })
                    .onFailure(t -> {
                        logger.error("Failed to get dividends", t);
                        ctx.fail(500, t);
                    });
        } catch (Exception e) {
            ctx.fail(400, new IllegalArgumentException("Invalid parameters: " + e.getMessage()));
        }
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

        // First resolve user UUID from email
        userRepo.getUserIdByEmail(userId)
                .compose(userUuid -> {
                    // Then get user secret to query SnapTrade
                    return userRepo.getUserSecret(userId)
                            .compose(secret -> {
                                if (secret == null) {
                                    throw new RuntimeException("User not registered with SnapTrade");
                                }
                                // Fetch dividend activities from SnapTrade
                                return snapTradeService.getDividendActivities(userId, secret, startDate, endDate)
                                        .map(activities -> new Object[] { userUuid, activities });
                            });
                })
                .compose(data -> {
                    UUID userUuid = (UUID) data[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<com.konfigthis.client.model.UniversalActivity> activities = (java.util.List<com.konfigthis.client.model.UniversalActivity>) data[1];

                    // Save each dividend activity to database
                    java.util.List<io.vertx.core.Future<Dividend>> saveFutures = new java.util.ArrayList<>();

                    for (com.konfigthis.client.model.UniversalActivity activity : activities) {
                        // Get SnapTrade account ID from activity
                        String snapTradeAccountId = activity.getAccount() != null
                                ? activity.getAccount().getId().toString()
                                : null;

                        if (snapTradeAccountId == null)
                            continue;

                        // Look up the broker_account in our database using account_number
                        io.vertx.core.Future<Dividend> dividendFuture = brokerAccountRepo
                                .findByAccountNumber(userUuid, snapTradeAccountId)
                                .compose(brokerAccount -> {
                                    if (brokerAccount == null) {
                                        logger.warn("No broker account found for SnapTrade account ID: {}",
                                                snapTradeAccountId);
                                        return io.vertx.core.Future.succeededFuture(null);
                                    }

                                    // Map activity to Dividend model using our broker_account.id
                                    Dividend dividend = Dividend.builder()
                                            .accountId(brokerAccount.getId())
                                            .symbol(activity.getSymbol() != null
                                                    ? activity.getSymbol().getSymbol()
                                                    : "UNKNOWN")
                                            .payDate(activity.getTradeDate() != null
                                                    ? activity.getTradeDate().toLocalDate()
                                                    : java.time.LocalDate.now())
                                            .amount(activity.getPrice() != null
                                                    ? new java.math.BigDecimal(activity.getPrice().toString())
                                                    : java.math.BigDecimal.ZERO)
                                            .exDate(null)
                                            .build();

                                    return dividendRepo.save(dividend);
                                });

                        saveFutures.add(dividendFuture);
                    }

                    return io.vertx.core.CompositeFuture.all(new java.util.ArrayList<>(saveFutures))
                            .map(cf -> {
                                // Filter out nulls (skipped dividends)
                                long savedCount = saveFutures.stream()
                                        .filter(f -> f.succeeded() && f.result() != null)
                                        .count();
                                return new JsonObject()
                                        .put("status", "success")
                                        .put("dividends_synced", savedCount);
                            });
                })
                .onSuccess(res -> ctx.json(res))
                .onFailure(t -> {
                    logger.error("Failed to sync dividends", t);
                    ctx.fail(500, t);
                });
    }
}
