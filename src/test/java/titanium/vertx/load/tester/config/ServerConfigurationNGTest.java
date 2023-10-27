/*
 * ServerConfigurationNGTest.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester.config;

import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class ServerConfigurationNGTest {

    @Test
    public void defaultConfigTest() {

        JsonObject json = new JsonObject();
        ServerConfiguration config = new ServerConfiguration(json);

        assertEquals(config.getHost(), "localhost");
        assertEquals(config.getPort(), 8080);
        assertEquals(config.getStatusCode(), 200);
        assertEquals(config.getHeaders().size(), 0);
        assertNull(config.getBody());
        assertEquals(config.getVerticles(), VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
        assertEquals(config.getMultiplexingLimit(), 1_000);
        assertEquals(config.getBlockingNanos(), 0);
        assertFalse(config.isExecuteBlocking());
    }

    @Test
    public void configTest() {

        JsonObject json = new JsonObject();
        json.put("host", "1.1.1.1");
        json.put("port", 9090);
        json.put("statusCode", 302);
        json.put("body", "{}");
        json.put("verticles", 1);
        json.put("multiplexingLimit", 2_000);
        json.put("blockingNanos", 100_000);
        json.put("executeBlocking", true);

        // create header one
        JsonObject header1 = new JsonObject();
        header1.put("name", "test-header");
        header1.put("value", "test-value");

        // create header two
        JsonObject header2 = new JsonObject();
        header2.put("name", "content-type");
        header2.put("value", "application/json");

        // add headers to header array
        JsonArray headers = new JsonArray();
        headers.add(header1);
        headers.add(header2);

        // add header array to json object
        json.put("headers", headers);

        ServerConfiguration config = new ServerConfiguration(json);

        assertEquals(config.getHost(), "1.1.1.1");
        assertEquals(config.getPort(), 9090);
        assertEquals(config.getStatusCode(), 302);
        assertEquals(config.getHeaders().size(), 2);
        assertEquals(config.getBody(), "{}");
        assertEquals(config.getVerticles(), 1);
        assertEquals(config.getMultiplexingLimit(), 2_000);
        assertEquals(config.getBlockingNanos(), 100_000);
        assertTrue(config.isExecuteBlocking());

        // assert header one
        assertTrue(config.getHeaders().contains("test-header"));
        assertEquals(config.getHeaders().get("test-header"), "test-value");

        // assert header two
        assertTrue(config.getHeaders().contains("content-type"));
        assertEquals(config.getHeaders().get("content-type"), "application/json");
    }

}
