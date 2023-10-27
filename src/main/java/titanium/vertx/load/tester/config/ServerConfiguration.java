/*
 * ServerConfiguration.java
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
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Contains server configuration.
 */
public class ServerConfiguration {
    
    private final String host;
    private final int port;
    private final int statusCode;
    private final MultiMap headers;
    private final String body;
    private final int verticles;
    private final int multiplexingLimit;
    private final int blockingNanos;
    private final boolean executeBlocking;
    
    public ServerConfiguration(JsonObject config) {
        this.host = config.getString("host", "localhost");
        this.port = config.getInteger("port", 8080);
        this.statusCode = config.getInteger("statusCode", 200);
        this.headers = MultiMap.caseInsensitiveMultiMap();
        this.body = config.getString("body", null);
        this.verticles = config.getInteger("verticles", VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE);
        this.multiplexingLimit = config.getInteger("multiplexingLimit", 1_000);
        this.blockingNanos = config.getInteger("blockingNanos", 0);
        this.executeBlocking = config.getBoolean("executeBlocking", false);
        
        if (config.containsKey("headers")) {
            JsonArray headerList = config.getJsonArray("headers");
            for (int i = 0; i < headerList.size(); i++) {
                JsonObject header = headerList.getJsonObject(i);
                this.headers.add(header.getString("name"), header.getString("value"));
            }
        }
    }

    public ServerConfiguration(String host, int port, int statusCode, MultiMap headers, String body, int verticles, int multiplexingLimit, int blockingNanos, boolean executeBlocking) {
        this.host = host;
        this.port = port;
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body;
        this.verticles = verticles;
        this.multiplexingLimit = multiplexingLimit;
        this.blockingNanos = blockingNanos;
        this.executeBlocking = executeBlocking;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public MultiMap getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public int getVerticles() {
        return verticles;
    }

    public int getMultiplexingLimit() {
        return multiplexingLimit;
    }

    public int getBlockingNanos() {
        return blockingNanos;
    }

    public boolean isExecuteBlocking() {
        return executeBlocking;
    }
    
}
