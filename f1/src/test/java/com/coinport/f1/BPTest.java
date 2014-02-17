/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import static org.junit.Assert.*;

import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Test;

import com.coinport.f1.command.CommandEvent;

@RunWith(JUnit4.class)
public class BPTest {
    @Test
    public void testRegisterUserCommand() throws Exception {
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        bp.setStopParams(latch, 3);
        bp.start();

        addUser(bp, 1234, "hoss", "0101");
        addUser(bp, 56, "chao", "0202");
        addUser(bp, 78, "ma", "0303");

        latch.await();
        bp.terminate();

        BusinessContext bc = bp.getContext();
        assertEquals(3, bc.userNum());

        assertUser(bc, 1234, "hoss", "0101");
        assertUser(bc, 56, "chao", "0202");
        assertUser(bc, 78, "ma", "0303");
    }

    @Test
    public void testDWCommand() throws Exception {
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        bp.start();
        bp.setStopParams(latch, 4);

        addUser(bp, 1234, "hoss", "0101");
        addUser(bp, 56, "chao", "0202");
        addUser(bp, 78, "ma", "0303");

        depositWithdrawal(bp, 1234, DOW.DEPOSIT, CoinType.CNY, 10000000);

        latch.await();

        UserInfo ui = bp.getContext().getUser(1234);
        assertEquals(1, ui.getWallets().size());
        Wallet wallet = ui.getWallets().get(CoinType.CNY);
        assertEquals(CoinType.CNY, wallet.getCoinType());
        assertEquals(10000000, wallet.getValid());
        assertFalse(wallet.isSetFrozen());

        latch = new CountDownLatch(1);
        bp.setMore(latch, 3);

        depositWithdrawal(bp, 1234, DOW.WITHDRAWAL, CoinType.CNY, 10000);
        depositWithdrawal(bp, 56, DOW.DEPOSIT, CoinType.BTC, 20);
        depositWithdrawal(bp, 56, DOW.WITHDRAWAL, CoinType.BTC, 4);

        latch.await();
        bp.terminate();

        assertEquals(9990000, bp.getContext().getUser(1234).getWallets().get(CoinType.CNY).getValid());
        assertEquals(16, bp.getContext().getUser(56).getWallets().get(CoinType.BTC).getValid());
    }

    @Test
    public void testPlaceOrder() throws Exception {
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        bp.start();
        bp.setStopParams(latch, 15);

        addUser(bp, 1234, "hoss", "0101");
        addUser(bp, 56, "chao", "0202");
        addUser(bp, 78, "ma", "0303");

        depositWithdrawal(bp, 1234, DOW.DEPOSIT, CoinType.CNY, 10000000);
        depositWithdrawal(bp, 56, DOW.DEPOSIT, CoinType.CNY, 10000000);
        depositWithdrawal(bp, 78, DOW.DEPOSIT, CoinType.CNY, 10000000);

        depositWithdrawal(bp, 1234, DOW.DEPOSIT, CoinType.BTC, 100);
        depositWithdrawal(bp, 56, DOW.DEPOSIT, CoinType.BTC, 100);
        depositWithdrawal(bp, 78, DOW.DEPOSIT, CoinType.BTC, 100);

        placeOrder(bp, 1, 1234, new TradePair(CoinType.CNY, CoinType.BTC), 9, 100001, BOS.BUY, 4001);
        placeOrder(bp, 2, 56, new TradePair(CoinType.CNY, CoinType.BTC), 2, 100002, BOS.SELL, 5001);
        placeOrder(bp, 3, 56, new TradePair(CoinType.CNY, CoinType.BTC), 2, 100003, BOS.SELL, 4001);
        placeOrder(bp, 4, 78, new TradePair(CoinType.CNY, CoinType.BTC), 12, 100004, BOS.SELL, 4031);
        placeOrder(bp, 5, 78, new TradePair(CoinType.CNY, CoinType.BTC), 31, 100005, BOS.BUY, 4001);
        placeOrder(bp, 6, 78, new TradePair(CoinType.CNY, CoinType.BTC), 4, 100005, BOS.BUY, 4001);

        latch.await();

        BlackBoard bb = bp.getContext().getBlackBoard(new TradePair(CoinType.CNY, CoinType.BTC));
        TreeSet buyList = bb.getBuyList();
        TreeSet sellList = bb.getSellList();
        assertEquals(4001, bb.getCurrentPrice());
        assertEquals(3, buyList.size());
        assertEquals(2, sellList.size());
        OrderInfo oi = bb.getFirstBuyOrder();
        assertEquals(7, oi.getQuantity());
        oi = bb.getFirstSellOrder();
        assertEquals(12, oi.getQuantity());
        assertEquals(4031, oi.getPrice());

        UserInfo ui = bp.getContext().getUser(1234);
        Wallet cnyWallet = ui.getWallets().get(CoinType.CNY);
        Wallet btcWallet = ui.getWallets().get(CoinType.BTC);
        assertEquals(10000000 - 4001 * 9, cnyWallet.getValid());
        assertEquals(4001 * 7, cnyWallet.getFrozen());
        assertEquals(102, btcWallet.getValid());
        assertEquals(0, btcWallet.getFrozen());

        ui = bp.getContext().getUser(56);
        cnyWallet = ui.getWallets().get(CoinType.CNY);
        btcWallet = ui.getWallets().get(CoinType.BTC);
        assertEquals(96, btcWallet.getValid());
        assertEquals(2, btcWallet.getFrozen());
        assertEquals(10000000 + 4001 * 2, cnyWallet.getValid());
        assertEquals(0, cnyWallet.getFrozen());

        latch = new CountDownLatch(1);
        bp.setMore(latch, 3);

        placeOrder(bp, 7, 56, new TradePair(CoinType.CNY, CoinType.BTC), 34, 100035, BOS.SELL, 3001);
        placeOrder(bp, 8, 56, new TradePair(CoinType.CNY, CoinType.BTC), 3, 100045, BOS.SELL, 5001);
        placeOrder(bp, 9, 56, new TradePair(CoinType.CNY, CoinType.BTC), 3, 10005, BOS.BUY, 1030);


        latch.await();

        assertEquals(3, buyList.size());
        oi = bb.getFirstBuyOrder();
        assertEquals(4, oi.getQuantity());
        assertEquals(5, oi.getId());

        latch = new CountDownLatch(1);
        bp.setMore(latch, 1);

        placeOrder(bp, 10, 78, new TradePair(CoinType.CNY, CoinType.BTC), 13, 100075, BOS.BUY, 4032);

        latch.await();

        assertEquals(4, buyList.size());
        assertEquals(2, sellList.size());
        oi = bb.getFirstBuyOrder();
        assertEquals(1, oi.getQuantity());

        latch = new CountDownLatch(1);
        bp.setMore(latch, 1);

        placeOrder(bp, 11, 78, new TradePair(CoinType.CNY, CoinType.BTC), 50, 100076, BOS.SELL , 102);

        latch.await();


        assertEquals(0, buyList.size());
        assertEquals(3, sellList.size());
        oi = bb.getFirstSellOrder();
        assertEquals(38, oi.getQuantity());
        bp.terminate();
    }

    @Test
    public void testCancelOrder() throws Exception {
        BP bp = new BP();
        CountDownLatch latch = new CountDownLatch(1);
        bp.start();
        bp.setStopParams(latch, 12);

        addUser(bp, 1234, "hoss", "0101");
        addUser(bp, 56, "chao", "0202");
        addUser(bp, 78, "ma", "0303");

        depositWithdrawal(bp, 1234, DOW.DEPOSIT, CoinType.CNY, 10000000);
        depositWithdrawal(bp, 56, DOW.DEPOSIT, CoinType.CNY, 10000000);
        depositWithdrawal(bp, 78, DOW.DEPOSIT, CoinType.CNY, 10000000);

        depositWithdrawal(bp, 1234, DOW.DEPOSIT, CoinType.BTC, 100);
        depositWithdrawal(bp, 56, DOW.DEPOSIT, CoinType.BTC, 100);
        depositWithdrawal(bp, 78, DOW.DEPOSIT, CoinType.BTC, 100);

        placeOrder(bp, 1, 1234, new TradePair(CoinType.CNY, CoinType.BTC), 9, 100001, BOS.BUY, 4001);
        placeOrder(bp, 2, 56, new TradePair(CoinType.CNY, CoinType.BTC), 2, 100002, BOS.SELL, 5001);
        placeOrder(bp, 3, 78, new TradePair(CoinType.CNY, CoinType.BTC), 3, 100003, BOS.SELL, 3001);

        latch.await();

        latch = new CountDownLatch(1);
        bp.setMore(latch, 2);

        cancelOrder(bp, 1, 1234, new TradePair(CoinType.CNY, CoinType.BTC));
        cancelOrder(bp, 2, 56, new TradePair(CoinType.CNY, CoinType.BTC));

        latch.await();
        bp.displayBC();

        BlackBoard bb = bp.getContext().getBlackBoard(new TradePair(CoinType.CNY, CoinType.BTC));
        TreeSet buyList = bb.getBuyList();
        TreeSet sellList = bb.getSellList();
        assertEquals(0, buyList.size());
        assertEquals(0, sellList.size());

        UserInfo ui = bp.getContext().getUser(1234);
        Wallet cnyWallet = ui.getWallets().get(CoinType.CNY);
        Wallet btcWallet = ui.getWallets().get(CoinType.BTC);
        assertEquals(10000000 - 4001 * 3, cnyWallet.getValid());
        assertEquals(0, cnyWallet.getFrozen());
        assertEquals(103, btcWallet.getValid());
        assertEquals(0, btcWallet.getFrozen());

        ui = bp.getContext().getUser(56);
        cnyWallet = ui.getWallets().get(CoinType.CNY);
        btcWallet = ui.getWallets().get(CoinType.BTC);
        assertEquals(10000000, cnyWallet.getValid());
        assertEquals(0, cnyWallet.getFrozen());
        assertEquals(100, btcWallet.getValid());
        assertEquals(0, btcWallet.getFrozen());

        ui = bp.getContext().getUser(78);
        cnyWallet = ui.getWallets().get(CoinType.CNY);
        btcWallet = ui.getWallets().get(CoinType.BTC);
        assertEquals(10000000 + 4001 * 3, cnyWallet.getValid());
        assertEquals(0, cnyWallet.getFrozen());
        assertEquals(97, btcWallet.getValid());
        assertEquals(0, btcWallet.getFrozen());

        bp.terminate();
    }

    private void cancelOrder(BP bp, final long id, final long uid, final TradePair tp) {
        OrderInfo oi = bp.nextCancelOrder();
        oi.setId(id);
        oi.setUid(uid);
        oi.setTradePair(tp);
        bp.execute();
    }

    private void placeOrder(BP bp, final long id, final long uid, final TradePair tp,
        final int quantity, final long timestamp, final BOS bos, final long price) {
        OrderInfo oi = bp.nextPlaceOrder();
        oi.setId(id);
        oi.setUid(uid);
        oi.setTradePair(tp);
        oi.setQuantity(quantity);
        oi.setTimestamp(timestamp);
        oi.setBos(bos);
        oi.setPrice(price);
        bp.execute();
    }

    private void depositWithdrawal(
        BP bp, final long uid, final DOW dwtype, final CoinType coinType, final long amount) {

        DWInfo dwi = bp.nextDepositWithdrawal();
        dwi.setUid(uid);
        dwi.setDwtype(dwtype);
        dwi.setCoinType(coinType);
        dwi.setAmount(amount);
        bp.execute();
    }

    private void addUser(BP bp, final long uid, final String name, final String pw) {
        UserInfo ui = bp.nextRegisterUser();
        ui.setId(uid);
        ui.setNickname(name);
        ui.setPassword(pw);
        bp.execute();
    }

    private void assertUser(BusinessContext bc, final long uid, final String name, final String pw) {
        UserInfo ui = bc.getUser(uid);
        assertEquals(name, ui.getNickname());
        assertEquals(pw, ui.getPassword());
    }
}
