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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import titanium.vertx.load.tester.config.ClientConfiguration;

/**
 * An http client/thread with one connection that sends X number of requests per
 * second.
 */
public class Client extends Thread {

    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);
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

        RUNNING.set(true);
        metrics.start();

        // create web client options
        WebClientOptions options = new WebClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false)
                .setHttp2MaxPoolSize(config.getNumberOfConnections())
                .setHttp2MultiplexingLimit(config.getMultiplexingLimit());
        
        // create client
        WebClient client = WebClient.create(vertx, options);

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

        while (RUNNING.get()) {
            if (streams.get() <= (config.getNumberOfConnections() * config.getMultiplexingLimit())) {
                try {
                    final long requestTime = System.nanoTime();
                    Future<HttpResponse<Buffer>> future;

                    if (body == null) {
                        future = request.send();
                    } else {
                        future = request.sendBuffer(body);
                    }

                    streams.incrementAndGet(); // stream opened
                    future.onComplete(handler -> {
                        streams.decrementAndGet(); // stream closed

                        if (handler.failed()) {
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
                } catch (Throwable th) {
                    th.printStackTrace();
                    this.interrupt();
                }
            }
        }
    }

    @Override
    public void interrupt() {
        if (RUNNING.getAndSet(false)) {
            vertx.setTimer(1_000, handler -> {
                // wait a sec for all transactions to complete then close vertx 
                // else a bunch of broken pipe exceptions could be thrown
                vertx.close();
            });
        }
    }

}
