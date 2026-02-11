package com.zenith.trade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;
import com.fnproject.fn.api.httpgateway.HTTPGatewayContext;
import com.zenith.trade.repository.TradeRepository;
import com.zenith.trade.repository.UserRepository;
import com.zenith.trade.util.DatabaseClient;
import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCPool;

import java.util.logging.Logger;

public class SyncProcessorFunction {

    private static final Logger logger = Logger.getLogger(SyncProcessorFunction.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

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

    public String handleRequest(HTTPGatewayContext ctx) {
        logger.info("Sync Processor triggered");
        
        String email = getEmail(ctx);
        if (email == null) {
            ctx.setStatusCode(401);
            return "{\"error\": \"Unauthorized\"}";
        }

        // Logic to trigger sync would go here.
        // 1. Get User ID
        // 2. Fetch connection details (SnapTrade secrets)
        // 3. Call SnapTrade API
        // 4. Save Trades via TradeRepository
        
        // For now, simple stub response.
        logger.info("Starting sync for user: " + email);
        
        // We could start an async process here, but Fn execution model usually waits.
        // If this is a long running process, we should offload to OCI Queue or Object Storage trigger.
        // For short syncs, we can do it here.

        return "{\"status\": \"Sync initiated\", \"user\": \"" + email + "\"}";
    }

    private String getEmail(HTTPGatewayContext ctx) {
         return ctx.getHeaders().get("X-User-Email")
               .orElse(ctx.getHeaders().get("x-user-email").orElse("test@zenith.com"));
    }
}
