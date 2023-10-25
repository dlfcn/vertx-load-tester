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
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.ArrayList;
import java.util.List;

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
    private final List<Client> clients = new ArrayList<>();

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
            
            Client client = new Client(vertx, 
                    options, 
                    tpsPerConnection, 
                    tpsTimer,
                    method, host, port, path);
            
            clients.add(client);
        }
    }

    public long getAverageTps() {
        return this.tpsTimer.getAverageTps();
    }
    
    @Override
    public void run() {
        tpsTimer.start(vertx);
        for (Client client : clients) {
            client.start();
        }
    }
    
    @Override
    public void interrupt() {
        for (Client clients : clients) {
            clients.getClient().close();
            clients.interrupt();
        }
        
        tpsTimer.stop();
        vertx.close();
        
        if (TESTER != null) {
            TESTER = null;
        }
    }

}
