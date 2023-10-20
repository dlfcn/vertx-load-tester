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
import java.util.concurrent.atomic.AtomicLong;
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
            TESTER.destroy();
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
         */
        return new Object[][]{
            {
                false, 2, 1_000, 1_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            {
                false, 10, 6_000, 2_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            {
                true, 2, 1_000, 1_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            {
                true, 10, 6_000, 2_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            }
        };
    }

    @Test(dataProvider = "loadProvider")
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
        deployLocalVerticles(SERVER_VERTX, port, multiplexingLimit, blockingNanos, executeBlocking);
        Thread.sleep(1_000); // wait a sec for verticles to start

        CLIENT_VERTX = Vertx.vertx();
        TESTER = new VertxLoadTester(CLIENT_VERTX,
                numberOfConnections,
                tpsPerConnection,
                multiplexingLimit,
                method, host, port, path);

        Thread.sleep(5_000); // wait a sec for tps buckets to fill

        boolean desiredTpsReached = false;
        int counter = 0;

        while (!desiredTpsReached) {
            if (LocalVerticle.AVERAGE_TPS >= (numberOfConnections * tpsPerConnection)) {
                desiredTpsReached = true;
                break;
            } else if (counter++ == 20) {
                // desired tps cannot be reached
                break;
            } else {
                // will take time for tps buckets to fill
                Thread.sleep(10_000);
            }
        }

        assertTrue(desiredTpsReached, String.format("Desired TPS of [%s] was not reached within 120 seconds", (numberOfConnections * tpsPerConnection)));
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

        LocalVerticle.start(vertx);
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

        private static final AtomicInteger INDEX = new AtomicInteger(0);
        private static final AtomicLong[] BUCKETS = new AtomicLong[61];
        private static long TPS_TIMER_ID = -1;
        private static long AVERAGE_TPS = 0; // for the last 60 seconds
        private final int port;
        private final int multiplexingLimit;
        private final long blockingNanos;
        private final boolean executeBlocking;
        private HttpServer httpServer;

        static {
            for (int i = 0; i < 61; i++) {
                BUCKETS[i] = new AtomicLong(0);
            }
        }

        public LocalVerticle(int port, int multiplexingLimit, long blockingNanos, boolean executeBlocking) {
            this.port = port;
            this.multiplexingLimit = multiplexingLimit;
            this.blockingNanos = blockingNanos;
            this.executeBlocking = executeBlocking;
        }

        public static void start(Vertx vertx) {

            TPS_TIMER_ID = vertx.setPeriodic(1_000, handler -> {

                int index = 0;

                if (INDEX.get() == (BUCKETS.length - 1)) {
                    INDEX.set(0);
                    BUCKETS[0].set(0);
                } else {
                    index = INDEX.incrementAndGet();
                    BUCKETS[index].set(0);
                }

                long total = 0;

                for (int i = 0; i < BUCKETS.length; i++) {
                    if (i != index) {
                        total = total + BUCKETS[i].get();
                    }
                }

                AVERAGE_TPS = (total / 60);
                System.out.printf("TPS = [%s]\n", AVERAGE_TPS);
            });
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
                            Future<HttpServerResponse> future = worker.executeBlocking(handler -> {
                                this.executeServiceLogic();
                                handler.complete(requestHandler.response());
                            }, false);

                            future.onComplete(handler -> {
                                handler.result().end();
                                BUCKETS[INDEX.get()].incrementAndGet();
                            });
                        } else {
                            // execute service logic on event loop thread
                            this.executeServiceLogic();
                            requestHandler.response().end();
                            BUCKETS[INDEX.get()].incrementAndGet();
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
            
            if (TPS_TIMER_ID != -1) {
                this.vertx.cancelTimer(TPS_TIMER_ID);
                TPS_TIMER_ID = -1;
                AVERAGE_TPS = 0;
                for (AtomicLong bucket : BUCKETS) {
                    bucket.set(0);
                }
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
