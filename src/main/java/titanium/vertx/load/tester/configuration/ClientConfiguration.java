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
package titanium.vertx.load.tester.configuration;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Contains client configuration.
 */
public class ClientConfiguration {
    
    private final JsonObject config;
    private final int numberOfConnections;
    private final int tpsPerConnection;
    private final int multiplexingLimit;
    private final HttpMethod httpMethod;
    private final String host;
    private final int port;
    private final String path;
    private final MultiMap headers;
    private final String body;
    
    public ClientConfiguration(JsonObject config) {
        this.config = config;
        this.numberOfConnections = config.getInteger("numberOfConnections", 1);
        this.tpsPerConnection = config.getInteger("tpsPerConnection", 100);
        this.multiplexingLimit = config.getInteger("multiplexingLimit", 1000);
        this.httpMethod = HttpMethod.valueOf(config.getString("httpMethod", "GET"));
        this.host = config.getString("host", "localhost");
        this.port = config.getInteger("port", 8080);
        this.path = config.getString("path", "/");
        this.headers = MultiMap.caseInsensitiveMultiMap();
        this.body = config.getString("body", null);
        
        if (config.containsKey("headers")) {
            JsonArray headerList = config.getJsonArray("headers");
            for (int i = 0; i < headerList.size(); i++) {
                JsonObject header = headerList.getJsonObject(i);
                this.headers.add(header.getString("name"), header.getString("value"));
            }
        }
    }

    public JsonObject getConfig() {
        return config;
    }

    public int getNumberOfConnections() {
        return numberOfConnections;
    }

    public int getTpsPerConnection() {
        return tpsPerConnection;
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
    
}
