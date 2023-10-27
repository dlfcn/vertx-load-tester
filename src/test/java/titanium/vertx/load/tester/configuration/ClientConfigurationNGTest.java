/*
 * ClientConfigurationNGTest.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester.configuration;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class ClientConfigurationNGTest {

    @Test
    public void defaultConfigTest() {

        JsonObject json = new JsonObject();
        ClientConfiguration config = new ClientConfiguration(json);

        assertEquals(config.getConfig().encode(), json.encode());
        assertEquals(config.getNumberOfConnections(), 1);
        assertEquals(config.getTpsPerConnection(), 100);
        assertEquals(config.getMultiplexingLimit(), 1_000);
        assertEquals(config.getHttpMethod(), HttpMethod.GET);
        assertEquals(config.getHost(), "localhost");
        assertEquals(config.getPort(), 8080);
        assertEquals(config.getPath(), "/");
        assertEquals(config.getHeaders().size(), 0);
        assertNull(config.getBody());
    }

    @Test
    public void configTest() {

        JsonObject json = new JsonObject();
        json.put("numberOfConnections", 2);
        json.put("tpsPerConnection", 200);
        json.put("multiplexingLimit", 2_000);
        json.put("httpMethod", "POST");
        json.put("host", "1.1.1.1");
        json.put("port", 9090);
        json.put("path", "/test/path");
        json.put("body", "{}");

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

        ClientConfiguration config = new ClientConfiguration(json);

        assertEquals(config.getConfig().encode(), json.encode());
        assertEquals(config.getNumberOfConnections(), 2);
        assertEquals(config.getTpsPerConnection(), 200);
        assertEquals(config.getMultiplexingLimit(), 2_000);
        assertEquals(config.getHttpMethod(), HttpMethod.POST);
        assertEquals(config.getHost(), "1.1.1.1");
        assertEquals(config.getPort(), 9090);
        assertEquals(config.getPath(), "/test/path");
        assertEquals(config.getHeaders().size(), 2);
        assertEquals(config.getBody(), "{}");

        // assert header one
        assertTrue(config.getHeaders().contains("test-header"));
        assertEquals(config.getHeaders().get("test-header"), "test-value");

        // assert header two
        assertTrue(config.getHeaders().contains("content-type"));
        assertEquals(config.getHeaders().get("content-type"), "application/json");
    }

}
