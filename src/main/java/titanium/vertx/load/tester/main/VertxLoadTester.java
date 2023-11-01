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
package titanium.vertx.load.tester.main;

import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import titanium.vertx.load.tester.config.ClientConfiguration;
import titanium.vertx.load.tester.config.ServerConfiguration;
import titanium.vertx.load.tester.config.TestConfiguration;

public class VertxLoadTester extends Thread {

    private static VertxLoadTester INSTANCE = null;

    /**
     * First argument must be "client" or "server". The seconds argument must 
     * be the path to the configuration file. See README file for expected 
     * json schema.
     * 
     * @param args to start client or server
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            throw new IllegalArgumentException("Zero arguments provided!");
        } else if (args.length < 2) {
            throw new IllegalArgumentException("Two arguments required!");
        } else if (!args[0].equals("client") && !args[0].equals("server")) {
            throw new IllegalArgumentException("First argument must be [client] or [server]!");
        }

        int argIndex = 0;
        boolean client = args[argIndex++].equals("client");

        if (client) {
            ClientConfiguration config = TestConfiguration.getClientConfiguration(args[1]);
            INSTANCE = new VertxLoadTester(Vertx.vertx(), config);
        } else {
            ServerConfiguration config = TestConfiguration.getServerConfiguration(args[1]);
            INSTANCE = new VertxLoadTester(Vertx.vertx(), config);
        }
        
        INSTANCE.start();
    }

    private final Metrics metrics; // used by all clients and server verticles
    private final List<Client> clientList = new ArrayList<>();
    private final Server server;

    /**
     * Creates a client load tester.
     *
     * @param vertx used to create web clients that send http requests
     * @param config for the clients
     */
    public VertxLoadTester(Vertx vertx, ClientConfiguration config) {
        this.metrics = new Metrics(vertx, true);
        this.server = null;
        for (int i = 0; i < config.getNumberOfClients(); i++) {
            clientList.add(new Client(vertx, config, metrics));
        }
    }

    /**
     * Creates a server load tester.
     *
     * @param vertx used to create server verticles
     * @param config for the server
     */
    public VertxLoadTester(Vertx vertx, ServerConfiguration config) {
        this.metrics = new Metrics(vertx, false);
        this.server = new Server(vertx, config, metrics);
    }

    public Metrics getMetrics() {
        return metrics;
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
        for (Client client : clientList) {
            client.interrupt();
        }

        // stop server
        if (server != null) {
            server.stop();
        }
    }

}
