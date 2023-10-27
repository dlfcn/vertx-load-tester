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
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.concurrent.TimeUnit;

/**
 * An http client/thread with one connection that sends X number of requests 
 * per second.
 */
public class Client extends Thread {

    private boolean running = true;
    private final Vertx vertx;
    private final int tpsPerConnection;
    private final TpsTimer tpsTimer;
    private final WebClient client;
    private final HttpRequest<Buffer> request;

    public Client(Vertx vertx, WebClientOptions clientOptions,
            int tpsPerConnection, TpsTimer tpsTimer,
            HttpMethod method, String host, int port, String path) {

        this.vertx = vertx;
        this.tpsPerConnection = tpsPerConnection;
        this.tpsTimer = tpsTimer;
        this.client = WebClient.create(vertx, clientOptions);
        this.request = client.request(method, port, host, path);
    }

    @Override
    public void run() {
        tpsTimer.start();
        while (running) {
            try {
                long startTime = System.currentTimeMillis();
                int counter = 0;

                while (true) {
                    final long requestTime = System.nanoTime();
                    request.send(handler -> {
                        long responseTime = System.nanoTime();
                        tpsTimer.log(responseTime - requestTime);
                    });

                    if (++counter == tpsPerConnection) {
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
