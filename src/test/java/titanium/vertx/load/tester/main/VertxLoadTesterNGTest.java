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
        return new Object[][]{
            /*
            Non cpu intensive test.
            Expect TPS = 2k+
            Two clients each sending over one connection.
            No server latency.
             */
            {
                false, 0, 
                2, 1, 1_000, 
                HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            /*
            Fairly cpu intesive test.
            Expected TPS = 10k
            Example showing how vertx utilizes connections based on settings.
            1k max concurrent streams per second over 10 connections.
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