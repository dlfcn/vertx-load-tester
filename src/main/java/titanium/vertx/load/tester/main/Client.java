/*
 * Client.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester.main;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import titanium.vertx.load.tester.config.ClientConfiguration;

/**
 * An http client/thread with one connection that sends X number of requests 
 * per second.
 */
public class Client extends Thread {

    private boolean running = true;
    private final Vertx vertx;
    private final ClientConfiguration config;
    private final TpsTimer tpsTimer;

    public Client(Vertx vertx, ClientConfiguration config, TpsTimer tpsTimer) {
        this.vertx = vertx;
        this.config = config;
        this.tpsTimer = tpsTimer;
    }

    @Override
    public void run() {
        
        tpsTimer.start();
        
        // do NOT change max pool size! One connection per thread/client!
        WebClientOptions clientOptions = new WebClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false)
                .setHttp2MaxPoolSize(1)
                .setHttp2MultiplexingLimit(config.getMultiplexingLimit());
        
        // create client/connection
        WebClient client = WebClient.create(vertx, clientOptions);
        
        // create request
        HttpRequest<Buffer> request = client.request(config.getHttpMethod(), 
                config.getPort(), 
                config.getHost(), 
                config.getPath());
        
        // add headers to request
        request.headers().addAll(config.getHeaders());
        
        // create body buffer
        Buffer body = null;
        if (config.getBody() != null) {
            body = Buffer.buffer(config.getBody());
        }
        
        while (running) {
            try {
                long startTime = System.currentTimeMillis();
                int counter = 0;

                while (true) {
                    final long requestTime = System.nanoTime();
                    
                    if (body == null) {
                        request.send(handler -> {
                            long responseTime = System.nanoTime();
                            tpsTimer.log(responseTime - requestTime);
                        });
                    } else {
                        request.sendBuffer(body, handler -> {
                            long responseTime = System.nanoTime();
                            tpsTimer.log(responseTime - requestTime);
                        });
                    }
                    
                    if (++counter == config.getTpsPerConnection()) {
                        break;
                    }
                }

                long sleepMillis = (startTime + 1_000) - System.currentTimeMillis();

                if (sleepMillis > 0) {
                    Thread.sleep(sleepMillis);
                }
            } catch (Throwable ex) {
                // do nothing
            }
        }
    }

    @Override
    public void interrupt() {
        running = false;
        vertx.close();
    }
    
}
