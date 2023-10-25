/*
 * Server.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.WorkerExecutor;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An http server with a number of event loops threads equal to 2x cpu cores.
 */
public class Server {

    private final Vertx vertx;
    private final TpsTimer tpsTimer;
    private final HttpServerOptions serverOptions;
    private final long blockingNanos;
    private final boolean executeBlocking;

    public Server(Vertx vertx, HttpServerOptions serverOptions,
            TpsTimer tpsTimer, long blockingNanos, boolean executeBlocking) {

        this.vertx = vertx;
        this.tpsTimer = tpsTimer;
        this.serverOptions = serverOptions;
        this.blockingNanos = blockingNanos;
        this.executeBlocking = executeBlocking;
    }

    public void stop() {
        vertx.close();
    }

    public void start() {

        int numberOfVerticles = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;
        System.out.printf("Deploying [%s] verticles.\n", numberOfVerticles);

        this.tpsTimer.start();
        this.deployLocalVerticle(new AtomicInteger(0), numberOfVerticles);
    }

    private void deployLocalVerticle(final AtomicInteger counter, final int verticles) {
        vertx.deployVerticle(new LocalVerticle(tpsTimer, serverOptions, blockingNanos, executeBlocking), handler -> {
            if (counter.incrementAndGet() < verticles) {
                this.deployLocalVerticle(counter, verticles);
            }
        });
    }

    private class LocalVerticle extends AbstractVerticle {

        private final TpsTimer tpsTimer;
        private final HttpServerOptions serverOptions;
        private final long blockingNanos;
        private final boolean executeBlocking;
        private HttpServer httpServer;

        public LocalVerticle(TpsTimer tpsTimer,
                HttpServerOptions serverOptions,
                long blockingNanos,
                boolean executeBlocking) {

            this.tpsTimer = tpsTimer;
            this.serverOptions = serverOptions;
            this.blockingNanos = blockingNanos;
            this.executeBlocking = executeBlocking;
        }

        @Override
        public void start() throws Exception {

            WorkerExecutor worker = this.vertx.createSharedWorkerExecutor(
                    "worker", 20, 100, TimeUnit.MILLISECONDS);

            this.vertx.createHttpServer(serverOptions)
                    .exceptionHandler(exceptionHandler -> {
                        exceptionHandler.printStackTrace();
                    })
                    .connectionHandler(connectionHandler -> {
                        System.out.println("Connection created.");
                    })
                    .requestHandler(requestHandler -> {
                        if (this.executeBlocking) {
                            // offload service logic processing to worker thread
                            // call me if you are going to do something crazy.
                            Future<HttpServerResponse> future = worker.executeBlocking(handler -> {
                                this.executeServiceLogic();
                                handler.complete(requestHandler.response());
                            }, false);

                            future.onComplete(handler -> {
                                handler.result().end();
                                tpsTimer.increment();
                            });
                        } else {
                            // execute service logic on event loop thread!!!!!!!
                            // DO NOT BLOCK VERTX EVENT LOOP!!!!!!!!!
                            this.executeServiceLogic();
                            requestHandler.response().end();
                            tpsTimer.increment();
                        }
                    })
                    .listen(h -> {
                        if (h.succeeded()) {
                            httpServer = h.result();
                            System.out.println("Verticle listening.");
                        } else {
                            h.cause().printStackTrace();
                        }
                    });
        }

        @Override
        public void stop() throws Exception {
            if (this.httpServer != null) {
                this.httpServer.close();
                this.httpServer = null;
            }
        }

        private void executeServiceLogic() {
            long endWorkTime = System.nanoTime() + this.blockingNanos;
            while (System.nanoTime() < endWorkTime) {
                // simulate time to execute service logic
            }
        }
    }

}
