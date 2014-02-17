/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.perftest;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;

import com.coinport.f1.*;

public class Main {
    final static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void perfTest() throws Exception {
        long userNum = 1000L * 200L;
        // long userNum = 4;

        PerfTest pt = new PerfTest();

        pt.test(0, userNum);
        pt.test(1, userNum * 100L);
        pt.test(2, userNum * 100L);

        // pt.display();
        pt.terminate();

        /*
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        long eventNum = 1024L * 1024L * 100L;
        bp.setStopParams(latch, eventNum);
        bp.start();

        UserInfo ui = bp.nextRegisterUser();
        ui.setId(1234);
        ui.setNickname("hoss");
        ui.setPassword("1111");
        bp.execute();

        long start = System.currentTimeMillis();
        for (long i = 0; i < eventNum; ++i) {
            DWInfo dwi = bp.nextDepositWithdrawal();
            dwi.setUid(1234);
            dwi.setDwtype(DOW.DEPOSIT);
            dwi.setCoinType(CoinType.CNY);
            dwi.setAmount(1);
            bp.execute();
        }
        latch.await();
        bp.terminate();
        long opsPerSecond = (eventNum * 1000L) / (System.currentTimeMillis() - start);

        String res = String.format("The ops is %,d ops/sec\n",Long.valueOf(opsPerSecond));
        logger.debug(res);
        bp.displayBC();
        */
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
