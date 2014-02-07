/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import static org.junit.Assert.*;

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
        bp.shutdown();
        // bp.displayBC();

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
        bp.setStopParams(latch, 4);
        bp.start();

        addUser(bp, 1234, "hoss", "0101");
        addUser(bp, 56, "chao", "0202");
        addUser(bp, 78, "ma", "0303");

        depositWithdrawal(bp, 1234, DOW.DEPOSIT, CoinType.CNY, 10000000);

        latch.await();
        bp.terminate();

        UserInfo ui = bp.getContext().getUser(1234);
        assertEquals(1, ui.getWallets().size());
        Wallet wallet = ui.getWallets().get(CoinType.CNY);
        assertEquals(CoinType.CNY, wallet.getCoinType());
        assertEquals(10000000, wallet.getValid());
        assertFalse(wallet.isSetFrozen());

        latch = new CountDownLatch(1);
        bp.setStopParams(latch, 1);
        bp.start();

        depositWithdrawal(bp, 1234, DOW.WITHDRAWAL, CoinType.CNY, 10000);

        latch.await();
        bp.terminate();

        bp.displayBC();
        System.out.println(bp.getContext().getUser(1234).getWallets().get(CoinType.CNY).getValid());
        assertEquals(9990000, bp.getContext().getUser(1234).getWallets().get(CoinType.CNY).getValid());


        bp.shutdown();
    }

    private void depositWithdrawal(
        BP bp, final long uid, final DOW dwtype, final CoinType coinType, final long amount) {
        CommandEvent event = bp.nextCommand();
        BPCommand bpc = new BPCommand();
        bpc.setType(BPCommandType.DW);
        DWInfo dwi = new DWInfo();
        dwi.setUid(uid);
        dwi.setDwtype(dwtype);
        dwi.setCoinType(coinType);
        dwi.setAmount(amount);
        bpc.setDwInfo(dwi);
        event.setCommand(bpc);
        bp.execute();
    }

    private void addUser(BP bp, final long uid, final String name, final String pw) {
        CommandEvent event = bp.nextCommand();
        BPCommand bpc = new BPCommand();
        bpc.setType(BPCommandType.REGISTER_USER);
        UserInfo ui = new UserInfo();
        ui.setId(uid);
        ui.setNickname(name);
        ui.setPassword(pw);
        bpc.setUserInfo(ui);
        event.setCommand(bpc);
        bp.execute();
    }

    private void assertUser(BusinessContext bc, final long uid, final String name, final String pw) {
        UserInfo ui = bc.getUser(uid);
        assertEquals(name, ui.getNickname());
        assertEquals(pw, ui.getPassword());
    }
}
