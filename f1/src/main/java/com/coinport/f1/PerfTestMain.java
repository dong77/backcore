/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import com.coinport.f1.command.CommandEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;

public class PerfTestMain {
    final static Logger logger = LoggerFactory.getLogger(PerfTestMain.class);

    public static void perfTest() throws Exception {
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        long eventNum = 1024L * 1024L * 100L;
        bp.setStopParams(latch, eventNum);
        bp.start();

        CommandEvent event = bp.nextCommand();
        BPCommand bpc = event.getCommand();
        bpc.setType(BPCommandType.REGISTER_USER);
        UserInfo ui = new UserInfo();
        ui.setId(1234);
        ui.setNickname("hoss");
        ui.setPassword("1111");
        bpc.setUserInfo(ui);
        bpc.setIndex(0);
        bp.execute();

        long start = System.currentTimeMillis();
        for (long i = 0; i < eventNum; ++i) {
            event = bp.nextCommand();

            bpc = event.getCommand();
            bpc.setType(BPCommandType.DW);
            if (bpc.isSetDwInfo()) {
                DWInfo dwi = bpc.getDwInfo();
                dwi.setUid(1234);
                dwi.setDwtype(DOW.DEPOSIT);
                dwi.setCoinType(CoinType.CNY);
                dwi.setAmount(1);
            } else {
                DWInfo dwi = new DWInfo();
                dwi.setUid(1234);
                dwi.setDwtype(DOW.DEPOSIT);
                dwi.setCoinType(CoinType.CNY);
                dwi.setAmount(1);
                bpc.setDwInfo(dwi);
            }
            bp.execute();
        }
        latch.await();
        bp.terminate();
        long opsPerSecond = (eventNum * 1000L) / (System.currentTimeMillis() - start);

        String res = String.format("The ops is %,d ops/sec\n",Long.valueOf(opsPerSecond));
        logger.debug(res);
        bp.displayBC();
    }

    public static void testLog() {
        logger.debug("this is a log test");

        // important!
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();

    }

    public static void treeSetTest() {
        TreeSet<OrderInfo> ts = new TreeSet<OrderInfo>(new OrderComparator(false));
        ts.add(addOrderInfo(2, 1001, 3, 1000001, 90));
        ts.add(addOrderInfo(3, 1001, 3, 1000003, 90));
        ts.add(addOrderInfo(2, 1001, 3, 1000003, 90));
        ts.add(addOrderInfo(2, 1001, 3, 1000003, 83));
        ts.add(addOrderInfo(2, 1001, 3, 1000025, 34));
        ts.add(addOrderInfo(2, 1001, 3, 1000041, 69));
        ts.add(addOrderInfo(2, 1001, 3, 1000011, 17));
        ts.add(addOrderInfo(2, 1001, 3, 1000003, 40));
        ts.add(addOrderInfo(2, 1001, 3, 1000005, 40));
        ts.add(addOrderInfo(1, 1001, 3, 1000009, 100));
        ts.add(addOrderInfo(1, 1001, 4, 1000009, 100));
        ts.add(addOrderInfo(1, 1001, 4, 1000009, 100));
        showTreeSet(ts);

        OrderInfo oi = ts.first();
        ts.remove(oi);
        showTreeSet(ts);
    }

    public static void main(String[] args) throws Exception {
        // testLog();
        perfTest();
        // treeSetTest();
    }

    private static void showTreeSet(final Set<OrderInfo> ts) {
        for (OrderInfo entry : ts) {
            logger.debug(entry.toString());
        }
    }

    private static OrderInfo addOrderInfo(
        final long id, final long uid, final int quantity, final long ts, final long price) {
        OrderInfo oi = new OrderInfo();
        oi.setId(id);
        oi.setUid(uid);
        oi.setQuantity(quantity);
        oi.setTimestamp(ts);
        oi.setPrice(price);
        return oi;
    }
}
