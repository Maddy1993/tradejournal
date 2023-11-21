package com.zenith.trade.journal;

import com.zenith.trade.journal.verticle.PetApiHandler;
import com.zenith.trade.journal.verticle.PetApiImpl;
import com.zenith.trade.journal.verticle.PetApiServiceImpl;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.openapi.RouterBuilderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    private static final String specFile = "api/src/main/resources/openapi.yaml";


    private final PetApiHandler petHandler = new PetApiHandler(new PetApiServiceImpl());

    @Override
    public void start(Promise<Void> startPromise) {
        RouterBuilder.create(vertx, specFile)
            .map(builder -> {
                builder.setOptions(new RouterBuilderOptions()
                    // For production use case, you need to enable this flag and provide the proper security handler
                    .setRequireSecurityHandlers(false)
                );

                petHandler.mount(builder);

                Router router = builder.createRouter();
                router.errorHandler(400, this::validationFailureHandler);

                return router;
            })
            .compose(router ->
                vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(8080)
            )
            .onSuccess(server -> logger.info("Http verticle deploy successful"))
            .onFailure(t -> logger.error("Http verticle failed to deploy", t))
            // Complete the start promise
            .<Void>mapEmpty().onComplete(startPromise);
    }

    private void validationFailureHandler(RoutingContext rc) {
        rc.response().setStatusCode(400)
            .end("Bad Request : " + rc.failure().getMessage());
    }
}
