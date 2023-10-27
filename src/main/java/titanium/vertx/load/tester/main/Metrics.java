/*
 * Metrics.java
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks average latency, average tps, and total transactions. Note that 
 * averages are based on the past sixty seconds.
 */
public class Metrics {
    
    private final Vertx vertx;
    private long timerId = -1;
    private final boolean client;
    private final AtomicInteger bucketIndex = new AtomicInteger(0);
    private final AtomicInteger[] tpsBuckets = new AtomicInteger[61];
    private long averageTps = 0; // for the last 60 seconds
    private final AtomicLong[] latencyBuckets = new AtomicLong[61];
    private long averageLatency = 0; // for the last 60 seconds
    private final AtomicLong totalTransactions = new AtomicLong(0);
    
    public Metrics(Vertx vertx, boolean client) {
        
        this.vertx = vertx;
        this.client = client;
        
        // initialize tps buckets
        for (int i = 0; i < tpsBuckets.length; i++) {
            tpsBuckets[i] = new AtomicInteger(0);
        }
        
        // intialize latency buckets
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new AtomicLong(0);
        }
    }

    public long getAverageTps() {
        return averageTps;
    }
    
    public long getAverageLatency() {
        return averageLatency;
    }
    
    public long getTotalTransactions() {
        return totalTransactions.get();
    }
    
    public void log(long latencyNanos) {
        int index = bucketIndex.get();
        tpsBuckets[index].incrementAndGet();
        latencyBuckets[index].addAndGet(latencyNanos);
        totalTransactions.incrementAndGet();
    }
    
    public synchronized void start() {

        // check if a client has already started timer
        if (this.timerId == -1) {

            // start timer task for client response tps
            this.timerId = this.vertx.setPeriodic(1_000, handler -> {

                int index = 0;

                if (bucketIndex.get() == (tpsBuckets.length - 1)) {
                    bucketIndex.set(0);
                    tpsBuckets[0].set(0);
                    latencyBuckets[0].set(0);
                } else {
                    index = bucketIndex.incrementAndGet();
                    tpsBuckets[index].set(0);
                    latencyBuckets[index].set(0);
                }

                long totalTps = 0;
                long totalLatency = 0;

                for (int i = 0; i < tpsBuckets.length; i++) {
                    if (i != index) {
                        // do not count current index
                        int tps = tpsBuckets[i].get();
                        long latency = latencyBuckets[i].get();
                        
                        if (tps > 0) {
                            totalTps = totalTps + tps;
                            totalLatency = totalLatency + (latency / tps);
                        }
                    }
                }

                averageTps = (totalTps / 60);
                averageLatency = (totalLatency / 60);

                System.out.printf("%s TPS = [%s], Latency Nanos = [%s], Total Transactions = [%s]\n",
                        client ? "Client" : "Server", averageTps, averageLatency, totalTransactions.get());
            });
        }
    }
    
}
