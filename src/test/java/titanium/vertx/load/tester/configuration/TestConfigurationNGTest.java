/*
 * TestConfigurationNGTest.java
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
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.JsonSchemaValidationException;
import io.vertx.json.schema.OutputUnit;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

public class TestConfigurationNGTest {
    
    private final String baseDirectory = "/Users/dfontana/NetBeansProjects/vertx-load-tester/src/test/resources/"; //todo - dynamically set this
    
    @Test
    public void validatorTest() {
        
        JsonObject json = new JsonObject();
        
        // valid config test
        json.put("client", new JsonObject().put("port", 9090));
        OutputUnit result = TestConfiguration.validate(json);
        
        assertNotNull(result);
        assertTrue(result.getValid());
        
        // invalid config test
        json.put("client", new JsonObject().put("port", "9090"));
        result = TestConfiguration.validate(json);
        
        assertNotNull(result);
        assertFalse(result.getValid());
        
        // invalid config test
        boolean validConfig = true;
        try {
            result.checkValidity();
        } catch (JsonSchemaValidationException ex) {
            validConfig = false;
        }
        assertFalse(validConfig);
    }

    @Test
    public void validClientConfigTest() {
    
        ClientConfiguration config = TestConfiguration.getClientConfiguration(baseDirectory + "client_config.json");
        
        assertEquals(config.getNumberOfConnections(), 10);
        assertEquals(config.getTpsPerConnection(), 1_000);
        assertEquals(config.getMultiplexingLimit(), 10_000);
        assertEquals(config.getHttpMethod(), HttpMethod.POST);
        assertEquals(config.getHost(), "localhost");
        assertEquals(config.getPort(), 8080);
        assertEquals(config.getPath(), "/nausf-auth/v1/ue-authentications/");
        assertEquals(config.getHeaders().size(), 2);
        assertEquals(config.getBody(), "{}");

        // assert header one
        assertTrue(config.getHeaders().contains("test-header"));
        assertEquals(config.getHeaders().get("test-header"), "test-value");

        // assert header two
        assertTrue(config.getHeaders().contains("content-type"));
        assertEquals(config.getHeaders().get("content-type"), "application/json");
    }
    
    @Test
    public void serverConfigTest() {
        
        ServerConfiguration config = TestConfiguration.getServerConfiguration(baseDirectory + "server_config.json");
        
        assertEquals(config.getHost(), "localhost");
        assertEquals(config.getPort(), 8080);
        assertEquals(config.getStatusCode(), 201);
        assertEquals(config.getHeaders().size(), 2);
        assertEquals(config.getBody(), "{}");
        assertEquals(config.getMultiplexingLimit(), 10_000);
        assertEquals(config.getBlockingNanos(), 0);
        assertFalse(config.isExecuteBlocking());

        // assert header one
        assertTrue(config.getHeaders().contains("test-header"));
        assertEquals(config.getHeaders().get("test-header"), "test-value");

        // assert header two
        assertTrue(config.getHeaders().contains("content-type"));
        assertEquals(config.getHeaders().get("content-type"), "application/json");
    }

}
