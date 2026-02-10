package com.zenith.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import com.zenith.trade.model.Trade;
import com.zenith.trade.repository.TradeRepository;
import com.zenith.trade.repository.UserRepository;
import com.zenith.trade.util.DatabaseClient;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCPool;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class TradesApiFunction {

    private static final Logger logger = Logger.getLogger(TradesApiFunction.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Vertx vertx;
    private JDBCPool dbClient;
    private TradeRepository tradeRepo;
    private UserRepository userRepo;

    @FnConfiguration
    public void config(RuntimeContext ctx) {
        this.vertx = Vertx.vertx();
        this.dbClient = DatabaseClient.getPool(vertx);
        this.tradeRepo = new TradeRepository(dbClient);
        this.userRepo = new UserRepository(dbClient);
    }

    public String handleRequest(InputStream input, HTTPGatewayContext ctx) {
        String method = ctx.getMethod();
        String path = ctx.getRequestURL();
        
        logger.info("Received request: " + method + " " + path);

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (path.endsWith("/stats")) {
                    return handleGetStats(ctx);
                } else {
                    return handleGetTrades(ctx);
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                return handleSaveTrade(input, ctx);
            }
            
            ctx.setStatusCode(405);
            return "Method Not Allowed";
        } catch (Exception e) {
            logger.severe("Error handling request: " + e.getMessage());
            e.printStackTrace();
            ctx.setStatusCode(500);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String handleGetTrades(HTTPGatewayContext ctx) throws ExecutionException, InterruptedException {
        String email = getEmail(ctx);
        if (email == null) {
            ctx.setStatusCode(401);
            return "{\"error\": \"Unauthorized\"}";
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        
        userRepo.getUserIdByEmail(email)
            .flatMap(userId -> tradeRepo.findAllByUserId(userId))
            .onSuccess(trades -> {
                try {
                    future.complete(mapper.writeValueAsString(trades));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            })
            .onFailure(future::completeExceptionally);

        return future.get();
    }

    private String handleGetStats(HTTPGatewayContext ctx) throws ExecutionException, InterruptedException {
        String email = getEmail(ctx);
        if (email == null) {
            ctx.setStatusCode(401);
            return "{\"error\": \"Unauthorized\"}";
        }
        
        CompletableFuture<String> future = new CompletableFuture<>();

        userRepo.getUserIdByEmail(email)
            .flatMap(userId -> tradeRepo.getStats(userId))
            .onSuccess(stats -> {
                try {
                    future.complete(mapper.writeValueAsString(stats));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            })
            .onFailure(future::completeExceptionally);

        return future.get();
    }

    private String handleSaveTrade(InputStream input, HTTPGatewayContext ctx) throws ExecutionException, InterruptedException, IOException {
        String email = getEmail(ctx);
        if (email == null) {
            ctx.setStatusCode(401);
            return "{\"error\": \"Unauthorized\"}";
        }

        if (input == null) {
             throw new IllegalArgumentException("Body required");
        }
        Trade trade = mapper.readValue(input, Trade.class);

        CompletableFuture<String> future = new CompletableFuture<>();

        userRepo.getUserIdByEmail(email)
            .compose(userId -> {
                // Determine account UUID. For now assume user ID or check checks. 
                // In reality we need to look up broker account by ID or use a default.
                // If trade has accountId, verify it belongs to user.
                
                if (trade.getAccountId() != null) {
                     // TODO: Verify account ownership
                     return Future.succeededFuture(trade);
                } else {
                    // Fallback or error. For now, fail if no account ID.
                    return Future.failedFuture("Account ID required");
                }
            })
            .compose(t -> tradeRepo.save(trade))
            .onSuccess(savedTrade -> {
                try {
                    future.complete(mapper.writeValueAsString(savedTrade));
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            })
            .onFailure(future::completeExceptionally);
            
        return future.get();
    }

    private String getEmail(HTTPGatewayContext ctx) {
        // Look for X-User-Email header
        return ctx.getHeaders().get("X-User-Email")
               .orElse(ctx.getHeaders().get("x-user-email").orElse("test@zenith.com"));
    }
}
