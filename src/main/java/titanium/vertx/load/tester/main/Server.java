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
package titanium.vertx.load.tester.main;

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
import titanium.vertx.load.tester.config.ServerConfiguration;

/**
 * An http server with a number of event loops threads equal to 2x cpu cores.
 */
public class Server {

    private final Vertx vertx;
    private final ServerConfiguration config;
    private final Metrics tpsTimer;

    public Server(Vertx vertx, ServerConfiguration config, Metrics tpsTimer) {
        this.vertx = vertx;
        this.config = config;
        this.tpsTimer = tpsTimer;
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
        vertx.deployVerticle(new LocalVerticle(config, tpsTimer), handler -> {
            if (counter.incrementAndGet() < verticles) {
                this.deployLocalVerticle(counter, verticles);
            }
        });
    }

    private class LocalVerticle extends AbstractVerticle {

        private final ServerConfiguration config;
        private final Metrics tpsTimer;
        private HttpServer httpServer;

        public LocalVerticle(ServerConfiguration config, Metrics tpsTimer) {
            this.config = config;
            this.tpsTimer = tpsTimer;
        }

        @Override
        public void start() throws Exception {
            
            HttpServerOptions serverOptions = new HttpServerOptions();
            serverOptions.getInitialSettings().setMaxConcurrentStreams(config.getMultiplexingLimit());
            serverOptions.setHost(config.getHost());
            serverOptions.setPort(config.getPort());

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
                        
                        long receiveTime = System.nanoTime();
                        
                        if (config.isExecuteBlocking()) {
                            // offload service logic processing to worker thread
                            // call me if you are going to do something crazy.
                            Future<HttpServerResponse> future = worker.executeBlocking(handler -> {
                                this.executeServiceLogic();
                                handler.complete(requestHandler.response());
                            }, false);

                            future.onComplete(handler -> {
                                this.sendResponse(handler.result());
                                tpsTimer.log(System.nanoTime() - receiveTime);
                            });
                        } else {
                            // execute service logic on event loop thread!!!!!!!
                            // DO NOT BLOCK VERTX EVENT LOOP!!!!!!!!!
                            this.executeServiceLogic();
                            this.sendResponse(requestHandler.response());
                            tpsTimer.log(System.nanoTime() - receiveTime);
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
            long endWorkTime = System.nanoTime() + config.getBlockingNanos();
            while (System.nanoTime() < endWorkTime) {
                // simulate time to execute service logic
            }
        }
        
        private void sendResponse(HttpServerResponse response) {
            
            response.setStatusCode(config.getStatusCode());
            response.headers().addAll(config.getHeaders());
            
            if (config.getBody() == null) {
                response.end();
            } else {
                response.end(config.getBody());
            }
        }
    }

}
