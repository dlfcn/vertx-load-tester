/*
 * VertxLoadTesterNGTest.java
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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class starts HTTP server verticles that the load tester client will send
 * requests to.
 */
public class VertxLoadTesterNGTest {

    private static Vertx CLIENT_VERTX = null;
    private static Vertx SERVER_VERTX = null;
    private static VertxLoadTester TESTER = null;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // do nothing
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception {

        if (TESTER != null) {
            TESTER.interrupt();
            TESTER = null;
        }

        if (CLIENT_VERTX != null) {
            CLIENT_VERTX.close();
            CLIENT_VERTX = null;
        }

        if (SERVER_VERTX != null) {
            SERVER_VERTX.close();
            SERVER_VERTX = null;
        }
    }

    @DataProvider
    public Object[][] loadProvider() {
        /*
        Note that each test will take roughly 60 seconds to execute.
        And tests might fail if you don't have enough CPU!
         */
        return new Object[][]{
            // Easy - Average TPS = 2k
            // 2k TPS = 2 Connections * trans
            // each transaction takes 1/10th of a millis
            {
                false, 2, 1_000, 1_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            // Hard - Beefy TPS = 30k
            // 30k TPS = 10 connections * 1k trans
            // each transaction takes 1/10th of a milli
            {
                false, 10, 3_000, 1_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            }
        };
    }

    @Test(dataProvider = "loadProvider", timeOut = 120000)
    public void test(boolean executeBlocking,
            int numberOfConnections, 
            int tpsPerConnection, 
            int multiplexingLimit, 
            long blockingNanos,
            HttpMethod method, String host, int port, String path)
            throws InterruptedException, Exception {

        tearDownClass();
        Thread.sleep(1_000); // wait a sec for threads and verticles to stop

        SERVER_VERTX = Vertx.vertx();
        deployLocalVerticles(SERVER_VERTX, 
                port, 
                multiplexingLimit, 
                blockingNanos, 
                executeBlocking);
        LocalVerticle.TPS_TIMER.start(SERVER_VERTX);
        Thread.sleep(1_000); // wait a sec for verticles to start

        CLIENT_VERTX = Vertx.vertx();
        TESTER = new VertxLoadTester(CLIENT_VERTX,
                numberOfConnections,
                tpsPerConnection,
                multiplexingLimit,
                method, host, port, path);
        TESTER.start();
        Thread.sleep(5_000); // wait a sec for tps buckets to fill

        boolean desiredTpsReached = false;

        while (!desiredTpsReached) {
            if (LocalVerticle.TPS_TIMER.getAverageTps() >= (numberOfConnections * tpsPerConnection)) {
                desiredTpsReached = true;
                break;
            } else {
                // will take time for tps buckets to fill
                Thread.sleep(10_000);
            }
        }

        assertTrue(desiredTpsReached, String.format(
                "Desired TPS of [%s] was not reached within 120 seconds", 
                (numberOfConnections * tpsPerConnection)));
    }

    private static void deployLocalVerticles(final Vertx vertx,
            final int port,
            final int multiplexingLimit,
            final long blockingNanos,
            final boolean executeBlocking) {

        int numberOfVerticles = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE;
        System.out.printf("Deploying [%s] verticles.\n", numberOfVerticles);

        deployLocalVerticle(vertx,
                port,
                multiplexingLimit,
                blockingNanos,
                executeBlocking,
                new AtomicInteger(0),
                numberOfVerticles);
    }

    private static void deployLocalVerticle(final Vertx vertx,
            final int port,
            final int multiplexingLimit,
            final long blockingNanos,
            final boolean executeBlocking,
            final AtomicInteger counter,
            final int verticles) {

        vertx.deployVerticle(new LocalVerticle(port, multiplexingLimit, blockingNanos, executeBlocking), handler -> {
            if (counter.incrementAndGet() < verticles) {

                deployLocalVerticle(vertx,
                        port,
                        multiplexingLimit,
                        blockingNanos,
                        executeBlocking,
                        counter,
                        verticles);
            }
        });
    }

    public static class LocalVerticle extends AbstractVerticle {

        private static final TpsTimer TPS_TIMER = new TpsTimer(false);
        private final int port;
        private final int multiplexingLimit;
        private final long blockingNanos;
        private final boolean executeBlocking;
        private HttpServer httpServer;

        public LocalVerticle(int port, int multiplexingLimit, long blockingNanos, boolean executeBlocking) {
            this.port = port;
            this.multiplexingLimit = multiplexingLimit;
            this.blockingNanos = blockingNanos;
            this.executeBlocking = executeBlocking;
        }

        @Override
        public void start() throws Exception {

            WorkerExecutor worker = this.vertx.createSharedWorkerExecutor(
                    "worker", 20, 100, TimeUnit.MILLISECONDS);

            HttpServerOptions options = new HttpServerOptions();
            options.getInitialSettings().setMaxConcurrentStreams(multiplexingLimit);
            options.setHost("localhost");
            options.setPort(port);

            this.vertx.createHttpServer(options)
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
                                TPS_TIMER.increment();
                            });
                        } else {
                            // execute service logic on event loop thread!!!!!!!
                            // DO NOT BLOCK VERTX EVENT LOOP!!!!!!!!!
                            this.executeServiceLogic();
                            requestHandler.response().end();
                            TPS_TIMER.increment();
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
            
            TPS_TIMER.stop();
        }
        
        private void executeServiceLogic() {
            long endWorkTime = System.nanoTime() + this.blockingNanos;
            while (System.nanoTime() < endWorkTime) {
                // simulate time to execute service logic
            }
        }
    }

}
