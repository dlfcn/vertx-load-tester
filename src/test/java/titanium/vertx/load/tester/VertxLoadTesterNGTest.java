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

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
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
            // Easy Test - Average TPS = 2k
            // 2k TPS = 2 Connections * 1k tps (per connection)
            // each transaction takes 1/10th of a millis
            {
                false, 2, 1_000, 1_000, 100_000, HttpMethod.POST, "localhost", 8080, "/nausf-auth/v1/ue-authentications/"
            },
            // Stress Test - Average TPS = 30k
            // 30k TPS = 10 connections * 1k tps (per connection)
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

        // create and start server
        SERVER = new VertxLoadTester(Vertx.vertx(), 
                port, 
                multiplexingLimit, 
                blockingNanos, 
                executeBlocking);
        SERVER.start();
        Thread.sleep(1_000); // wait a sec for verticles to start

        // create and start client
        CLIENT = new VertxLoadTester(Vertx.vertx(),
                numberOfConnections,
                tpsPerConnection,
                multiplexingLimit,
                method, host, port, path);
        CLIENT.start();
        Thread.sleep(5_000); // wait a sec for tps buckets to fill

        boolean desiredTpsReached = false;

        while (!desiredTpsReached) {
            if (SERVER.getAverageTps() >= (numberOfConnections * tpsPerConnection)) {
                desiredTpsReached = true;
            } else {
                // will take time for tps buckets to fill
                Thread.sleep(10_000);
            }
        }

        assertTrue(desiredTpsReached, String.format(
                "Desired TPS of [%s] was not reached within 120 seconds", 
                (numberOfConnections * tpsPerConnection)));
    }

}
