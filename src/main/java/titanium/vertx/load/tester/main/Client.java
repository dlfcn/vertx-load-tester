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

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import titanium.vertx.load.tester.config.ClientConfiguration;

/**
 * An http client/thread with one connection that sends X number of requests per
 * second.
 */
public class Client extends Thread {

    private static boolean RUNNING;
    private final Vertx vertx;
    private final ClientConfiguration config;
    private final Metrics metrics;

    public Client(Vertx vertx, ClientConfiguration config, Metrics metrics) {
        this.vertx = vertx;
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public void run() {

        RUNNING = true;
        metrics.start();

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

        while (RUNNING) {
            long startTime = System.currentTimeMillis();
            int counter = 0;

            while (RUNNING) {
                if (counter++ == config.getTpsPerConnection()) {
                    break; // desired number of transactions sent
                }
                
                final long requestTime = System.nanoTime();
                Future<HttpResponse<Buffer>> future;

                if (body == null) {
                    future = request.send();
                } else {
                    future = request.sendBuffer(body);
                }

                future.onComplete(handler -> {
                    if (!RUNNING) {
                        // something went wrong with one of the clients
                        this.interrupt();
                    } else if (handler.failed()) {
                        this.interrupt();
                        throw new RuntimeException(handler.cause());
                    } else if (handler.result().statusCode() != config.getExpectedStatusCode()) {
                        this.interrupt();
                        throw new RuntimeException(String.format(
                                "Expected status code [%s], received [%s].",
                                config.getExpectedStatusCode(),
                                handler.result().statusCode()));
                    } else {
                        long responseTime = System.nanoTime();
                        metrics.log(responseTime - requestTime);
                    }
                });
            }

            long sleepMillis = (startTime + 1_000) - System.currentTimeMillis();
            
            if (sleepMillis < 0) {
                this.interrupt();
                throw new RuntimeException(String.format(
                        "Could not send desired transactions per second [%s].", 
                        config.getTpsPerConnection()));
            } else {
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException ex) {
                    // do nothing
                }
            }
        }
    }

    @Override
    public void interrupt() {
        RUNNING = false;
        vertx.setTimer(1_000, handler -> {
            // wait a sec for all transactions to complete then close vertx 
            // else a bunch of broken pipe exceptions could be thrown
            vertx.close();
        });
    }
    
}
