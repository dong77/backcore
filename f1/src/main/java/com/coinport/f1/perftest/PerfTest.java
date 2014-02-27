/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.perftest;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;

public class PerfTest {
    final static Logger logger = LoggerFactory.getLogger(PerfTest.class);
    private long[] prices = null;

    private BP bp = new BP();

    private long userCount = 0;

    public PerfTest() {
        bp.setStopParams(null, 0);
        bp.start();
    }

    public void test(final int commandType, final long num) throws Exception {
        if (commandType == 2) {
            prices = new long[1048576];
            for (int i = 0; i < 1048576; ++i) {
                prices[i] = (long)gauseRandom(500.0, 100.0);
            }
        }
        CountDownLatch latch = new CountDownLatch(1);
        bp.setMore(latch, num);

        long start = System.currentTimeMillis();
        switch (commandType) {
            case 0:
                addUsers(num);
                break;
            case 1:
                dw(num);
                break;
            case 2:
                exchange(num, prices);
                break;
            default:
                break;
        }
        latch.await();
        long opsPerSecond = (num * 1000L) / (System.currentTimeMillis() - start);
        String res = String.format("The ops of command type %d is %,d ops/sec\n",
            commandType, Long.valueOf(opsPerSecond));
        logger.info(res);
    }

    public void addUsers(final long num) {
        userCount = num;
        for (long i = 0; i < num; ++i) {
            UserInfo ui = bp.nextRegisterUser();
            ui.setId(i);
            ui.setNickname("h");
            ui.setPassword("123");
            bp.execute();
        }
    }

    public void dw(final long num) {
        long half = num / 2;
        for (long i = 0; i < half; ++i) {
            DWInfo dwi = bp.nextDepositWithdrawal();
            dwi.setUid(i % userCount);
            dwi.setDwtype(DOW.DEPOSIT);
            dwi.setCoinType(CoinType.CNY);
            dwi.setAmount(10000);
            bp.execute();
        }
        for (long i = half; i < num; ++i) {
            DWInfo dwi = bp.nextDepositWithdrawal();
            dwi.setUid(i % userCount);
            dwi.setDwtype(DOW.DEPOSIT);
            dwi.setCoinType(CoinType.BTC);
            dwi.setAmount(10000);
            bp.execute();
        }
    }

    public void exchange(final long num, final long[] prices) {
        final TradePair tp = new TradePair(CoinType.CNY, CoinType.BTC);
        for (long i = 0; i < num; ++i) {
            OrderInfo oi = bp.nextPlaceOrder();

            oi.setId(i);
            oi.setUid(i % userCount);
            oi.setTradePair(tp);
            oi.setQuantity(1);
            oi.setTimestamp(i);
            oi.setBos(((i & 1) == 0) ? BOS.BUY : BOS.SELL);
            oi.setPrice(prices[(int)(i & 1048575)]);

            bp.execute();
        }
    }

    public void terminate() {
        bp.terminate();
    }

    public void display() {
        bp.displayBC();
    }

    private double gauseRandom(double a, double b) {
        double temp = 12;
        double x = 0;
        for (int i = 0; i < temp; i++)
            x = x + (Math.random());
        x = (x - temp / 2) / (Math.sqrt(temp / 12));
        x = a + x * Math.sqrt(b); return x;
    }
}
