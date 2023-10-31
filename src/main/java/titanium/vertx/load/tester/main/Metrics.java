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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks average latency, average tps, and total transactions. Note that 
 * averages are based on the past sixty seconds.
 */
public class Metrics {
    
    private final Vertx vertx;
    private long timerId1Second = -1;
    private long timerId15Seconds = -1;
    private final boolean client;
    private long maxTps = 0;
    private final AtomicInteger bucketIndex = new AtomicInteger(0);
    
    // client and server metrics
    private final AtomicInteger[] tpsBuckets = new AtomicInteger[61];
    private long averageTps = 0; // for the last 60 seconds
    private final AtomicLong[] latencyBuckets = new AtomicLong[61];
    private long averageLatency = 0; // for the last 60 seconds
    private final AtomicLong totalTransactions = new AtomicLong(0);
    
    // server only metrics
    private final ConcurrentHashMap<Integer, AtomicLong> remotePortMap = new ConcurrentHashMap<>(); // total per remote port
    
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

    public long getMaxTps() {
        return maxTps;
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
    
    public void logRemotePortTransaction(int remotePort) {
        
        // log transaction for remote client port
        if (remotePortMap.containsKey(remotePort)) {
            remotePortMap.get(remotePort).incrementAndGet();
        } else {
            remotePortMap.put(remotePort, new AtomicLong(1));
        }
    }
    
    public synchronized void start() {

        // server and client stats (so far)
        if (this.timerId1Second == -1) {
            this.timerId1Second = this.vertx.setPeriodic(1_000, handler -> {

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
                
                if (averageTps > maxTps) {
                    maxTps = averageTps;
                }

                System.out.printf("%s TPS = [%s], Latency Nanos = [%s], Total Transactions = [%s]\n",
                        client ? "Client" : "Server", averageTps, averageLatency, totalTransactions.get());
            });
        }

        // server only stats (so far)
        if (!client) {
            if (this.timerId15Seconds == -1) {
                this.timerId15Seconds = this.vertx.setPeriodic(15_000, handler -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n");
                    for (Map.Entry<Integer, AtomicLong> entry : remotePortMap.entrySet()) {
                        sb.append(String.format("Remote Port [%s] = [%s]\n",
                                entry.getKey(), entry.getValue().get()));
                    }
                    System.out.println(sb.toString());
                });
            }
        }
    }
    
}
