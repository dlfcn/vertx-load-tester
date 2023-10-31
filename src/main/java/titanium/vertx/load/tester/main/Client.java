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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import titanium.vertx.load.tester.config.ClientConfiguration;

/**
 * An http client/thread with one connection that sends X number of requests per
 * second.
 */
public class Client extends Thread {

    private boolean running = true;
    private final Vertx vertx;
    private final ClientConfiguration config;
    private final Metrics metrics;
    private final AtomicLong streams = new AtomicLong(0);

    public Client(Vertx vertx, ClientConfiguration config, Metrics metrics) {
        this.vertx = vertx;
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public void run() {

        metrics.start();

        // create web client options
        WebClientOptions clientOptions = new WebClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false)
                .setHttp2MaxPoolSize(1) // do NOT change!!
                .setHttp2MultiplexingLimit(config.getMultiplexingLimit());
        
        // each request in list created using a different web client
        // each request will be sent round-robin on its own connection
        List<HttpRequest<Buffer>> requestList = new ArrayList<>();
        
        for (int i = 0; i < config.getNumberOfConnections(); i++) {
            
            // create client/connection
            WebClient client = WebClient.create(vertx, clientOptions);

            // create request
            HttpRequest<Buffer> request = client.request(config.getHttpMethod(),
                    config.getPort(),
                    config.getHost(),
                    config.getPath());

            // add headers to request
            request.headers().addAll(config.getHeaders());
            
            // add request to list
            requestList.add(request);
        }

        // create body buffer
        Buffer body = null;
        if (config.getBody() != null) {
            body = Buffer.buffer(config.getBody());
        }
        
        int index = 0;

        while (running) {
            if (streams.get() <= (config.getNumberOfConnections() * config.getMultiplexingLimit())) {
                
                if (++index == (requestList.size())) {
                    index = 0;
                }

                final long requestTime = System.nanoTime();
                Future<HttpResponse<Buffer>> future;

                if (body == null) {
                    future = requestList.get(index).send();
                } else {
                    future = requestList.get(index).sendBuffer(body);
                }

                streams.incrementAndGet(); // stream opened
                future.onComplete(handler -> {
                    streams.decrementAndGet(); // stream closed

                    if (!running) {
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
        }
    }

    @Override
    public void interrupt() {
        running = false;
        vertx.setTimer(1_000, handler -> {
            // wait a sec for all transactions to complete then close vertx 
            // else a bunch of broken pipe exceptions could be thrown
            vertx.close();
        });
    }

}
