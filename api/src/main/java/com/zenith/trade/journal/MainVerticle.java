package com.zenith.trade.journal;

import com.zenith.trade.journal.dal.repository.BrokerAccountRepository;
import com.zenith.trade.journal.dal.repository.HoldingRepository;
import com.zenith.trade.journal.dal.repository.UserRepository;
import com.zenith.trade.journal.handler.BrokerageHandler;
import com.zenith.trade.journal.handler.DashboardHandler;
import com.zenith.trade.journal.service.SnapTradeService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.core.http.HttpMethod;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

        private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
        private PgPool client;

        @Override
        public void start(Promise<Void> startPromise) {
                // Load .env
                io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
                                .ignoreIfMissing()
                                .load();
                dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

                // Database Configuration
                PgConnectOptions connectOptions = new PgConnectOptions()
                                .setPort(Integer.parseInt(System.getProperty("DB_PORT", "5432")))
                                .setHost(System.getProperty("DB_HOST", "localhost"))
                                .setDatabase(System.getProperty("DB_NAME", "tradejournal"))
                                .setUser(System.getProperty("DB_USER", "postgres"))
                                .setPassword(System.getProperty("DB_PASSWORD", "secret"));

                PoolOptions poolOptions = new PoolOptions()
                                .setMaxSize(5);

                client = PgPool.pool(vertx, connectOptions, poolOptions);

                // Repositories
                // Repositories
                BrokerAccountRepository brokerAccountRepository = new BrokerAccountRepository(client);
                HoldingRepository holdingRepository = new HoldingRepository(client);
                com.zenith.trade.journal.dal.repository.DividendRepository dividendRepository = new com.zenith.trade.journal.dal.repository.DividendRepository(
                                client);
                UserRepository userRepository = new UserRepository(client);

                // Services
                SnapTradeService snapTradeService = new SnapTradeService(vertx, userRepository);

                // Handlers
                BrokerageHandler brokerageHandler = new BrokerageHandler(snapTradeService, brokerAccountRepository,
                                holdingRepository, userRepository);
                DashboardHandler dashboardHandler = new DashboardHandler(snapTradeService, holdingRepository,
                                userRepository);
                com.zenith.trade.journal.handler.PerformanceHandler performanceHandler = new com.zenith.trade.journal.handler.PerformanceHandler(
                                snapTradeService, userRepository);
                com.zenith.trade.journal.handler.DividendHandler dividendHandler = new com.zenith.trade.journal.handler.DividendHandler(
                                snapTradeService, dividendRepository, userRepository);

                Router router = Router.router(vertx);
                router.route().handler(CorsHandler.create("http://localhost:3000")
                                .allowedMethod(HttpMethod.GET)
                                .allowedMethod(HttpMethod.POST)
                                .allowedMethod(HttpMethod.OPTIONS)
                                .allowedHeader("Access-Control-Request-Method")
                                .allowedHeader("Access-Control-Allow-Credentials")
                                .allowedHeader("Access-Control-Allow-Origin")
                                .allowedHeader("Access-Control-Allow-Headers")
                                .allowedHeader("Content-Type"));
                router.route().handler(BodyHandler.create());

                router.post("/api/brokerage/sync").handler(brokerageHandler::syncHoldings);
                router.get("/api/holdings").handler(brokerageHandler::getHoldings);
                router.get("/api/dashboard").handler(dashboardHandler::getDashboardSummary);

                router.get("/health").handler(ctx -> ctx.response().end("OK"));

                // Brokerage Routes
                router.post("/api/brokerage/register").handler(brokerageHandler::registerUser);
                router.post("/api/brokerage/connect").handler(brokerageHandler::generateConnectionLink);
                router.post("/api/brokerage/sync").handler(brokerageHandler::syncHoldings);
                router.get("/api/brokerage/accounts").handler(brokerageHandler::getAccounts);

                // Performance Routes
                router.get("/api/performance").handler(performanceHandler::getPerformance);

                // Dividend Routes
                router.get("/api/dividends").handler(dividendHandler::getDividends);
                router.post("/api/dividends/sync").handler(dividendHandler::syncDividends);

                vertx.createHttpServer()
                                .requestHandler(router)
                                .listen(8080)
                                .onSuccess(server -> {
                                        logger.info("Http verticle deploy successful on port 8080");
                                        startPromise.complete();
                                })
                                .onFailure(t -> {
                                        logger.error("Http verticle failed to deploy", t);
                                        startPromise.fail(t);
                                });
        }

        @Override
        public void stop() {
                if (client != null) {
                        client.close();
                }
        }
}
