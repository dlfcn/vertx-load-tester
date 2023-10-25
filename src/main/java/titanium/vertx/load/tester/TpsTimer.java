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
    
    private Vertx vertx;
    private long timerId = -1;
    private final boolean isClient;
    private final AtomicInteger bucketIndex = new AtomicInteger(0);
    private final AtomicInteger[] buckets = new AtomicInteger[61];
    private long averageTps = 0; // for the last 60 seconds
    
    public TpsTimer(boolean isClient) {
        this.isClient = isClient;
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
    
    public void start(Vertx vertx) {
        
        if (this.vertx != null) {
            this.stop();
        }
        
        this.vertx = vertx;
        
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
                    isClient ? "Client" : "Server",  averageTps);
        });
    }
    
    public void stop() {
        
        // cancel timer and reset vertx
        if (vertx != null && timerId != -1) {
            vertx.cancelTimer(timerId);
            timerId = -1;
            vertx = null;
        }
        
        // reset buckets
        for (AtomicInteger bucket : buckets) {
            bucket.set(0);
        }
        
        // reset index and tps
        bucketIndex.set(0);
        averageTps = 0;
    }
}
