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
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.ArrayList;
import java.util.List;

public class VertxLoadTester extends Thread {

    private static VertxLoadTester INSTANCE = null;

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

        if (args.length == 0) {
            throw new IllegalArgumentException("Zero arguments provided!");
        } else if (!args[0].equals("client") && !args[0].equals("server")) {
            throw new IllegalArgumentException("First argument must be [client] or [server]!");
        }

        int argIndex = 0;
        boolean client = args[argIndex++].equals("client");

        if (client) {
            if ((args.length - 1) != 7) {
                throw new IllegalArgumentException("Additional client arguments required: "
                        + "int numberOfConnections, "
                        + "int tpsPerConnection, "
                        + "int multiplexingLimit, "
                        + "String httpMethod, "
                        + "String host, "
                        + "int port, "
                        + "String path");
            }

            // java VertxLoadTester client 10 1000 1000 POST localhost 8080 /nausf-auth/v1/ue-authentications/
            // should get 10k tps = 10 connections * 1k tps
            int numberOfConnections = Integer.parseInt(args[argIndex++]);
            int tpsPerConnection = Integer.parseInt(args[argIndex++]);
            int multiplexingLimit = Integer.parseInt(args[argIndex++]);
            HttpMethod method = HttpMethod.valueOf(args[argIndex++]);
            String host = args[argIndex++];
            int port = Integer.parseInt(args[argIndex++]);
            String path = args[argIndex++];

            INSTANCE = new VertxLoadTester(Vertx.vertx(),
                    numberOfConnections,
                    tpsPerConnection,
                    multiplexingLimit,
                    method, host, port, path);
        } else {
            if ((args.length - 1) != 4) {
                throw new IllegalArgumentException("Additional server arguments required: "
                        + "int port, "
                        + "int multiplexingLimit, "
                        + "int blockingNanos, "
                        + "boolean executeBlocking");
            }

            // java VertxLoadTester server 8080 1000 0 false
            int port = Integer.parseInt(args[argIndex++]);
            int multiplexingLimit = Integer.parseInt(args[argIndex++]);
            long blockingNanos = Long.parseLong(args[argIndex++]);
            boolean executeBlocking = Boolean.parseBoolean(args[argIndex++]);

            INSTANCE = new VertxLoadTester(Vertx.vertx(),
                    port,
                    multiplexingLimit,
                    blockingNanos,
                    executeBlocking);
        }
        
        INSTANCE.start();
    }

    private final TpsTimer tpsTimer; // used by all clients and server verticles
    private final List<Client> clientList = new ArrayList<>();
    private final Server server;

    /**
     * Creates a client load tester.
     *
     * @param vertx used to create web clients that send http requests
     * @param numberOfConnections number of clients that will be created
     * @param tpsPerConnection number of requests each client will send per
     * second
     * @param multiplexingLimit number of concurrent transactions supported
     * @param method of the http request
     * @param host of the http request
     * @param port of the http request
     * @param path of the http request
     */
    public VertxLoadTester(Vertx vertx,
            int numberOfConnections,
            int tpsPerConnection,
            int multiplexingLimit,
            HttpMethod method, String host, int port, String path) {

        this.tpsTimer = new TpsTimer(vertx, true);
        this.server = null;

        // do NOT change max pool size! One connection per thread/client!
        WebClientOptions clientOptions = new WebClientOptions()
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setHttp2ClearTextUpgrade(false)
                .setHttp2MaxPoolSize(1)
                .setHttp2MultiplexingLimit(multiplexingLimit);

        // create threads/clients for sending requests
        for (int i = 0; i < numberOfConnections; i++) {
            this.clientList.add(new Client(vertx,
                    clientOptions,
                    tpsPerConnection,
                    tpsTimer,
                    method, host, port, path));
        }
    }

    /**
     * Creates a server load tester.
     *
     * @param vertx used to create server verticles
     * @param port on which the service should listen
     * @param multiplexingLimit number of concurrent transactions supported
     * @param blockingNanos used to simulate duration to execute service logic
     * @param executeBlocking if processing should be done by worker thread
     * instead of event loop
     */
    public VertxLoadTester(Vertx vertx,
            int port,
            int multiplexingLimit,
            long blockingNanos,
            boolean executeBlocking) {
        
        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions.getInitialSettings().setMaxConcurrentStreams(multiplexingLimit);
        serverOptions.setHost("localhost");
        serverOptions.setPort(port);
                
        this.tpsTimer = new TpsTimer(vertx, false);
        this.server = new Server(vertx, serverOptions, tpsTimer, blockingNanos, executeBlocking);
    }

    public long getAverageTps() {
        return this.tpsTimer.getAverageTps();
    }

    @Override
    public void run() {

        // start server
        if (server != null) {
            server.start();
        }

        // start clients
        for (Client client : clientList) {
            client.start();
        }
    }
    
    @Override
    public void interrupt() {

        // stop clients
        for (Client clients : clientList) {
            clients.interrupt();
        }

        // stop server
        if (server != null) {
            server.stop();
        }
    }

}
