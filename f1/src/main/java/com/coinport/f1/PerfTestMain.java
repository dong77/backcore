/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.concurrent.CountDownLatch;

import com.coinport.f1.command.CommandEvent;

public class PerfTestMain {
    public static void perfTest() throws Exception {
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        long eventNum = 1000L * 1000L * 100L;
        bp.setStopParams(latch, eventNum);
        bp.start();
        long start = System.currentTimeMillis();
        for (long i = 0; i < eventNum; ++i) {
            CommandEvent event = bp.nextCommand();
            BPCommand bpc = new BPCommand();
            bpc.setType(BPCommandType.DW);
            event.setCommand(bpc);
            bp.execute();
        }
        latch.await();
        bp.terminate();
        long opsPerSecond = (eventNum * 1000L) / (System.currentTimeMillis() - start);

        System.out.format("The ops is %,d ops/sec\n",Long.valueOf(opsPerSecond));
        System.out.println("finish");
    }

    public static void main(String[] args) throws Exception {
        perfTest();
    }
}
