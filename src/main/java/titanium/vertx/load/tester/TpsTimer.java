/*
 * TpsTimer.java
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks average TPS over the past 60 seconds.
 */
public class TpsTimer {
    
    private final Vertx vertx;
    private long timerId = -1;
    private final boolean client;
    private final AtomicInteger bucketIndex = new AtomicInteger(0);
    private final AtomicInteger[] buckets = new AtomicInteger[61];
    private long averageTps = 0; // for the last 60 seconds
    
    public TpsTimer(Vertx vertx, boolean client) {
        
        this.vertx = vertx;
        this.client = client;
        
        // initialize tps buckets
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new AtomicInteger(0);
        }
    }

    public long getAverageTps() {
        return averageTps;
    }
    
    public void increment() {
        buckets[bucketIndex.get()].incrementAndGet();
    }
    
    public synchronized void start() {

        // check if a client has already started timer
        if (this.timerId == -1) {

            // start timer task for client response tps
            this.timerId = this.vertx.setPeriodic(1_000, handler -> {

                int index = 0;

                if (bucketIndex.get() == (buckets.length - 1)) {
                    bucketIndex.set(0);
                    buckets[0].set(0);
                } else {
                    index = bucketIndex.incrementAndGet();
                    buckets[index].set(0);
                }

                long total = 0;

                for (int i = 0; i < buckets.length; i++) {
                    if (i != index) {
                        // do not count current index in average tps
                        total = total + buckets[i].get();
                    }
                }

                averageTps = (total / 60);

                System.out.printf("%s TPS = [%s]\n",
                        client ? "Client" : "Server", averageTps);
            });
        }
    }
    
}
