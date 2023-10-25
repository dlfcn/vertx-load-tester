/*
 * Connection.java
 *
 * Copyright (c) 2023 Titanium Software Holdings Inc. All Rights Reserved.
 *
 * This software is the proprietary information of Titanium Software Holdings Inc. 
 * Use is subject to license terms.
 *
 * @author Titanium Software Holdings Inc.
 */
package titanium.vertx.load.tester;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.concurrent.TimeUnit;

/**
 * A client/thread with one connection that sends requests.
 */
public class Client extends Thread {

    private boolean running = true;
    private final int tpsPerConnection;
    private final TpsTimer tpsTimer;
    private final WebClient client;
    private final HttpRequest<Buffer> request;

    public Client(Vertx vertx, WebClientOptions webClientOptions,
            int tpsPerConnection, TpsTimer tpsTimer,
            HttpMethod method, String host, int port, String path) {

        this.tpsPerConnection = tpsPerConnection;
        this.tpsTimer = tpsTimer;
        this.client = WebClient.create(vertx, webClientOptions);
        this.request = client.request(method, port, host, path);
    }

    public WebClient getClient() {
        return client;
    }

    @Override
    public void run() {
        while (running) {
            try {
                long startTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(1);
                int counter = 0;

                while (true) {
                    request.send(handler -> {
                        tpsTimer.increment();
                    });

                    if (++counter == tpsPerConnection) {
                        break;
                    }
                }

                long sleepMillis = startTime - System.currentTimeMillis();

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
    }
    
}
