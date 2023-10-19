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
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * This class starts HTTP servers verticles that the load tester client will
 * send requests to.
 */
public class VertxLoadTesterNGTest {
    
    private static Vertx CLIENT_VERTX = null;
    private static Vertx SERVER_VERTX = null;
    private static VertxLoadTester TESTER = null;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // do nothing
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        SERVER_VERTX.close();
    }
    
    @DataProvider
    public Object[][] loadProvider() {
        /*
        Note that each test will take roughly 60 seconds to execute.
        And that adjusting the multiplexing limit per connection has an affect
        on achievable TPS. If too low TPS will take a while to average out, too high
        and the CPU spend too much time consuming from its TCP stream.
         */
        return new Object[][]{
            {
                2, 1_000, 1_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            {
                10, 6_000, 2_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            }
        };
    }
    
    @Test(dataProvider = "loadProvider")
    public void test(int numberOfConnections, int tpsPerConnection, int multiplexingLimit, 
            HttpMethod method, String host, int port, String path) 
            throws InterruptedException {
        
        if (TESTER != null) {
            TESTER.destroy();
        }
        
        if (CLIENT_VERTX != null) {
            CLIENT_VERTX.close();
        }
        
        if (SERVER_VERTX != null) {
            SERVER_VERTX.close();
        }
        
        Thread.sleep(500); // wait sec for threads and verticles to stop
        
        SERVER_VERTX = Vertx.vertx();
        deployLocalVerticles(SERVER_VERTX, port, multiplexingLimit);
        
        CLIENT_VERTX = Vertx.vertx();
        TESTER = new VertxLoadTester(CLIENT_VERTX, 
                numberOfConnections, 
                tpsPerConnection, 
                multiplexingLimit,
                method, host, port, path);
        
        Thread.sleep(5_000); // wait a couple seconds for tps buckets to fill

        boolean desiredTpsReached = false;
        int counter = 0;
        
        while (!desiredTpsReached) {
            if (LocalVerticle.AVERAGE_TPS >= (numberOfConnections * tpsPerConnection)) {
                desiredTpsReached = true;
                break;
            } else if (counter++ == 20) {
                break;
            } else {
                // will take time for tps buckets to fill
                Thread.sleep(10_000);
            }
        }
        
        assertTrue(desiredTpsReached, String.format("Desired TPS of [%s] was not reached within 60 seconds", (numberOfConnections * tpsPerConnection)));
    }

    private static void deployLocalVerticles(final Vertx vertx, final int port, final int multiplexingLimit) {
        
        deployLocalVerticle(vertx, 
                port,
                multiplexingLimit,
                new AtomicInteger(0), 
                VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
        
        LocalVerticle.start(vertx);
    }

    private static void deployLocalVerticle(final Vertx vertx, 
            final int port,
            final int multiplexingLimit,
            final AtomicInteger counter, 
            final int verticles) {
        
        vertx.deployVerticle(new LocalVerticle(port, multiplexingLimit), handler -> {
            if (counter.incrementAndGet() < verticles) {
                
                deployLocalVerticle(vertx, 
                        port, 
                        multiplexingLimit, 
                        counter, 
                        verticles);
            }
        });
    }

    public static class LocalVerticle extends AbstractVerticle {

        private static final AtomicInteger INDEX = new AtomicInteger(0);
        private static final List<AtomicLong> BUCKETS = new ArrayList<>();
        private static long AVERAGE_TPS = 0; // for the last 60 seconds
        private final int port;
        private final int multiplexingLimit;
        private HttpServer httpServer;
        
        static {
            for (int i = 0; i < 61; i++) {
                BUCKETS.add(new AtomicLong(0));
            }
        }
        
        public LocalVerticle(int port, int multiplexingLimit) {
            this.port = port;
            this.multiplexingLimit = multiplexingLimit;
        }
        
        public static void start(Vertx vertx) {
            
            vertx.setPeriodic(1_000, handler -> {
                
                int index = 0;
                
                if (INDEX.get() == (BUCKETS.size() - 1)) {
                    INDEX.set(0);
                    BUCKETS.get(0).set(0);
                } else {
                    index = INDEX.incrementAndGet();
                    BUCKETS.get(index).set(0);
                }
                
                long total = 0;
                
                for (int i = 0; i < BUCKETS.size(); i++) {
                    if (i != index) {
                        total = total + BUCKETS.get(i).get();
                    }
                }
                
                AVERAGE_TPS = (total / 60);
                System.out.printf("TPS = [%s]\n", AVERAGE_TPS);
            });
        }

        @Override
        public void start() throws Exception {
            
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
                        requestHandler.endHandler(endHandler -> {
                            requestHandler.response().end();
                            BUCKETS.get(INDEX.get()).incrementAndGet();
                        });
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
    }

}
