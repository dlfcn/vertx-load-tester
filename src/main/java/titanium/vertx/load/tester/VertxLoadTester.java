/*
 * VertxLoadTester.java
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
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VertxLoadTester extends Thread {
    
    private static VertxLoadTester TESTER = null;

    /**
     * Seven parameters in the following order are required to run this.
     *
     * 1. Desired number of connection(s) 2. Desired TPS per connection 3.
     * Multiplexing limit for each connection 4. HTTP method of the request 5.
     * Host of HTTP server being load tested 6. Port of HTTP server being load
     * tested 7. Path of the HTTP service being load tested
     *
     * @param args
     */
    public static void main(String[] args) {

        if (args.length < 7 || args.length > 7) {
            throw new IllegalArgumentException("Seven arguments must be provided.");
        }

        // java VertxLoadTester 10 1000 1000 POST localhost 8080 /nausf-auth/v1/ue-authentications/
        // should get 10k tps = 10 connections * 1k tps
        int numberOfConnections = Integer.parseInt(args[0]);
        int tpsPerConnection = Integer.parseInt(args[1]);
        int multiplexingLimit = Integer.parseInt(args[2]);
        HttpMethod method = HttpMethod.valueOf(args[3]);
        String host = args[4];
        int port = Integer.parseInt(args[5]);
        String path = args[6];

        TESTER = new VertxLoadTester(Vertx.vertx(),
                numberOfConnections,
                tpsPerConnection,
                multiplexingLimit,
                method, host, port, path);
    }

    private final Vertx vertx;
    private final TpsTimer tpsTimer = new TpsTimer(true);
    private final List<Connection> connections = new ArrayList<>();

    public VertxLoadTester(Vertx vertx, int numberOfConnections, int tpsPerConnection, int multiplexingLimit,
            HttpMethod method, String host, int port, String path) {
        
        this.vertx = vertx;

        // do NOT change max pool size! One connection per thread/client!
        WebClientOptions options = new WebClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false)
                .setHttp2MaxPoolSize(1)
                .setHttp2MultiplexingLimit(multiplexingLimit);

        // create threads/clients for sending requests
        for (int i = 0; i < numberOfConnections; i++) {
            
            Connection connection = new Connection(vertx, 
                    options, 
                    tpsPerConnection, 
                    tpsTimer,
                    method, host, port, path);
            
            connections.add(connection);
        }
    }

    public long getAverageTps() {
        return this.tpsTimer.getAverageTps();
    }
    
    @Override
    public void run() {
        tpsTimer.start(vertx);
        for (Connection connection : connections) {
            connection.start();
        }
    }
    
    @Override
    public void interrupt() {
        for (Connection connection : connections) {
            connection.client.close();
            connection.interrupt();
        }
        
        tpsTimer.stop();
        vertx.close();
        
        if (TESTER != null) {
            TESTER = null;
        }
    }

    private static class Connection extends Thread {

        private boolean running = true;
        private final int tpsPerConnection;
        private final TpsTimer tpsTimer;
        private final WebClient client;
        private final HttpRequest<Buffer> request;

        public Connection(Vertx vertx, WebClientOptions webClientOptions,
                int tpsPerConnection, TpsTimer tpsTimer,
                HttpMethod method, String host, int port, String path) {

            this.tpsPerConnection = tpsPerConnection;
            this.tpsTimer = tpsTimer;
            this.client = WebClient.create(vertx, webClientOptions);
            this.request = client.request(method, port, host, path);
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

}
