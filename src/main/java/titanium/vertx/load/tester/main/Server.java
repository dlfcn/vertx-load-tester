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
 * An http server with the desired number of verticles/event-loop-threads 
 * to process requests. Default number of verticles is 2x cpu cores. Each 
 * event loop thread processes requests for one TCP connection. Event loop 
 * threads are assigned new TCP connections in a round robin fashion.
 */
public class Server {

    private final Vertx vertx;
    private final ServerConfiguration config;
    private final Metrics metrics;

    public Server(Vertx vertx, ServerConfiguration config, Metrics metrics) {
        this.vertx = vertx;
        this.config = config;
        this.metrics = metrics;
    }

    public void stop() {
        vertx.close();
    }

    public void start() {
        System.out.printf("Deploying [%s] verticles.\n", VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
        this.metrics.start();
        this.deployLocalVerticle(new AtomicInteger(0));
    }

    private void deployLocalVerticle(final AtomicInteger counter) {
        vertx.deployVerticle(new LocalVerticle(config, metrics), handler -> {
            if (counter.incrementAndGet() < VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE) {
                this.deployLocalVerticle(counter);
            }
        });
    }

    private class LocalVerticle extends AbstractVerticle {

        private final ServerConfiguration config;
        private final Metrics metrics;
        private HttpServer httpServer;

        public LocalVerticle(ServerConfiguration config, Metrics metrics) {
            this.config = config;
            this.metrics = metrics;
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
                        int remotePort = requestHandler.connection().remoteAddress().port();
                        metrics.logRemotePortTransaction(remotePort);
                        
                        if (config.isExecuteBlocking()) {
                            // offload service logic processing to worker thread
                            // call me if you are going to do something crazy.
                            Future<HttpServerResponse> future = worker.executeBlocking(handler -> {
                                this.executeServiceLogic();
                                handler.complete(requestHandler.response());
                            }, false);

                            future.onComplete(handler -> {
                                this.sendResponse(handler.result());
                                metrics.log(System.nanoTime() - receiveTime);
                            });
                        } else {
                            // execute service logic on event loop thread!!!!!!!
                            // DO NOT BLOCK VERTX EVENT LOOP!!!!!!!!!
                            this.executeServiceLogic();
                            this.sendResponse(requestHandler.response());
                            metrics.log(System.nanoTime() - receiveTime);
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
            long endWorkTime = System.currentTimeMillis() + config.getBlockingMillis();
            while (System.currentTimeMillis() < endWorkTime) {
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
