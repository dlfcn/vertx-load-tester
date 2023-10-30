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

        if (SERVER != null) {
            SERVER.interrupt();
            SERVER = null;
        }
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
             */
            {
                false, 10, 3_000, 0, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            /*
            Expected TPS = 5k
            Server takes 2ms to process each request. Max TPS is equal to the 
            time each transaction takes to process using an event-loop-thread, 
            multiplied by the number of connections.
            
            Expected TPS = (1 sec / processing millis) * Number Of Connections
            500 tps = 1_000 millis / 2 millis;
            5k tps = 500 tps * 10 connections;
             */
            {
                false, 10, 3_000, 2, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            }
        };
    }

    @Test(dataProvider = "loadProvider", timeOut = 120000)
    public void test(boolean executeBlocking, int numberOfConnections, 
            int multiplexingLimit, long blockingMillis, 
            HttpMethod method, String host, int port, String path)
            throws InterruptedException, Exception {

        tearDownClass();
        Thread.sleep(1_000); // wait a sec for threads and verticles to stop
        
        // calculate expected transactions per second
        long expectedTps;
        if (blockingMillis > 0) {
            expectedTps = (1_000 / blockingMillis) * numberOfConnections;
        } else {
            expectedTps = numberOfConnections * multiplexingLimit;
        }
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
        ClientConfiguration clientConfig = new ClientConfiguration(numberOfConnections,
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
