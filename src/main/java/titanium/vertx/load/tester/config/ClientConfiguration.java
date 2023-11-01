/*
 * ClientConfiguration.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester.config;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Contains client configuration.
 */
public class ClientConfiguration {
    
    private final int numberOfClients;
    private final int numberOfConnections;
    private final int multiplexingLimit;
    private final HttpMethod httpMethod;
    private final String host;
    private final int port;
    private final String path;
    private final MultiMap headers;
    private final String body;
    private final int expectedStatusCode;
    
    public ClientConfiguration(JsonObject config) {
        
        this.numberOfClients = config.getInteger("numberOfClients", 1);
        this.numberOfConnections = config.getInteger("numberOfConnections", 1);
        this.multiplexingLimit = config.getInteger("multiplexingLimit", 1000);
        this.httpMethod = HttpMethod.valueOf(config.getString("httpMethod", "GET"));
        this.host = config.getString("host", "localhost");
        this.port = config.getInteger("port", 8080);
        this.path = config.getString("path", "/");
        this.headers = MultiMap.caseInsensitiveMultiMap();
        this.body = config.getString("body", null);
        this.expectedStatusCode = config.getInteger("expectedStatusCode", 200);
        
        if (config.containsKey("headers")) {
            JsonArray headerList = config.getJsonArray("headers");
            for (int i = 0; i < headerList.size(); i++) {
                JsonObject header = headerList.getJsonObject(i);
                this.headers.add(header.getString("name"), header.getString("value"));
            }
        }
    }

    public ClientConfiguration(int numberOfClients, int numberOfConnections, int multiplexingLimit, 
            HttpMethod httpMethod, String host, int port, String path, 
            MultiMap headers, String body, int expectedStatusCode) {
        
        this.numberOfClients = numberOfClients;
        this.numberOfConnections = numberOfConnections;
        this.multiplexingLimit = multiplexingLimit;
        this.httpMethod = httpMethod;
        this.host = host;
        this.port = port;
        this.path = path;
        this.headers = headers;
        this.body = body;
        this.expectedStatusCode = expectedStatusCode;
    }

    public int getNumberOfClients() {
        return numberOfClients;
    }

    public int getNumberOfConnections() {
        return numberOfConnections;
    }

    public int getMultiplexingLimit() {
        return multiplexingLimit;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getPath() {
        return path;
    }

    public MultiMap getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }
    
}
