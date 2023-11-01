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
package titanium.vertx.load.tester.main;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import titanium.vertx.load.tester.config.ClientConfiguration;
import titanium.vertx.load.tester.config.ServerConfiguration;

/**
 * This class starts HTTP server verticles that the load tester client will send
 * requests to.
 */
public class VertxLoadTesterNGTest {

    private static VertxLoadTester CLIENT = null;
    private static VertxLoadTester SERVER = null;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // do nothing
    }

    @AfterClass(alwaysRun = true)
    public static void tearDownClass() throws Exception {

        if (CLIENT != null) {
            CLIENT.interrupt();
            CLIENT = null;
        }
        
        Thread.sleep(2_000);

        if (SERVER != null) {
            SERVER.interrupt();
            SERVER = null;
        }
        
        Thread.sleep(2_000);
    }

    @DataProvider
    public Object[][] loadProvider() {
        /*
        Note that each test will take roughly 60 seconds to execute.
        And tests might fail if you don't have enough CPU!
         */
        return new Object[][]{
            /*
            Expected TPS = 30k+
            Server immediately responds 200.
            
            Monitoring the server for the number of requests from a single
            remote port shows that one connection can be favored if the 
            multiplexing limit is set too high. This is partly due to the server 
            taking zero time to process the request and send a response to 
            close the transaction and free up the stream on the connection. 
            Adding latency will ensure the client sends an even number of 
            requests over all available connections, thus utilizing more of a 
            servers available "verticles" (which are just "event-loop-threads").
             */
            {
                false, 0, 
                2, 1, 1_000, 
                HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            /*
            Expected TPS = ~5k
            Server takes 2ms to process each request. Max TPS is equal to the 
            time each transaction takes to process using an event-loop-thread, 
            multiplied by the number of connections.
            
            Expected TPS = (1 sec / processing millis) * Number Of Connections;
            500 tps = 1_000 millis / 2 millis;
            5k tps = 500 tps * 10 connections;
             */
//            {
//                false, 2, 
//                1, 10, 500, 
//                HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
//            },
            /*
            Expected TPS = ~10k
            Good connection scaling example
            1k tps per connection = 1 second / 1 ms (per request)
             */
//            {
//                false, 1, 
//                1, 10, 1_000, 
//                HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
//            }
        };
    }

    @Test(dataProvider = "loadProvider", timeOut = 120000)
    public void test(boolean executeBlocking, long blockingMillis, 
            int numberOfClients, int numberOfConnections, int multiplexingLimit,
            HttpMethod method, String host, int port, String path)
            throws InterruptedException, Exception {

        tearDownClass();
        
        // calculate expected transactions per second
        long expectedTps;
        if (blockingMillis > 0) {
            expectedTps = (1_000 / blockingMillis) * numberOfConnections;
        } else {
            expectedTps = numberOfConnections * multiplexingLimit;
        }
        expectedTps = numberOfClients * expectedTps;
        System.out.printf("Expected TPS = [%s]\n", expectedTps);

        // create and start server
        ServerConfiguration serverConfig = new ServerConfiguration(host, 
                port, 
                200, 
                MultiMap.caseInsensitiveMultiMap(),
                null,
                numberOfConnections,
                multiplexingLimit,
                blockingMillis,
                executeBlocking);
        
        SERVER = new VertxLoadTester(Vertx.vertx(), serverConfig);
        SERVER.start();
        Thread.sleep(1_000); // wait a sec for verticles to start

        // create and start client
        ClientConfiguration clientConfig = new ClientConfiguration(numberOfClients,
                numberOfConnections,
                multiplexingLimit,
                method, host, port, path,
                MultiMap.caseInsensitiveMultiMap(),
                null,
                200);
        
        CLIENT = new VertxLoadTester(Vertx.vertx(), clientConfig);
        CLIENT.start();
        Thread.sleep(5_000); // wait a sec for tps buckets to fill

        boolean expectedTpsReached = false;
        
        while (!expectedTpsReached) {
            if (CLIENT.getMetrics().getMaxTps() >= expectedTps
                    || SERVER.getMetrics().getMaxTps() >= expectedTps) {
                expectedTpsReached = true;
            } else {
                // will take time for tps buckets to fill
                Thread.sleep(10_000);
            }
        }

        assertTrue(expectedTpsReached, String.format(
                "Expected TPS of [%s] was not reached within 120 seconds", 
                (expectedTps)));
    }

}
