/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 * TODO(c): 目前这种bc调bb然后bb又回调bc的方法很不好，耦合太强，而且后期很难做到事务。需要尽快重构。
 */

package com.coinport.f1;

import java.util.HashMap;
import java.util.Map;

public class BusinessContext {
    private Map<Long, UserInfo> users;
    private Map<TradePair, BlackBoard> blackBoards;

    // Don't use this method in inner function for the performance concern.
    public UserInfo getUser(long uid) {
        return users.get(uid).deepCopy();
    }

    public long userNum() {
        return users.size();
    }

    public BusinessContext() {
        users = new HashMap<Long, UserInfo>();
        blackBoards = new HashMap<TradePair, BlackBoard>();
    }

    public void display() {
        for (Map.Entry<Long, UserInfo> entry : users.entrySet()) {
            System.out.println(entry.getKey() + "-->" + entry.getValue().toString());
        }
    }

    public boolean register(UserInfo ui) {
        users.put(ui.getId(), ui);
        return true;
    }

    public boolean deposit(final long uid, final CoinType coinType, final long amount, final boolean fromValid) {
        if (!users.containsKey(uid)) {
            System.out.println("can't find user to deposit");
            return false;
        }
        UserInfo ui = users.get(uid);
        if (!ui.isSetWallets()) {
            Map<CoinType, Wallet> wallets = new HashMap<CoinType, Wallet>();
            Wallet wallet = new Wallet();
            wallet.setCoinType(coinType);
            if (fromValid)
                wallet.setValid(amount);
            else
                wallet.setFrozen(amount);
            wallets.put(coinType, wallet);
            ui.setWallets(wallets);
        } else {
            Map<CoinType, Wallet> wallets = ui.getWallets();
            if (wallets.containsKey(coinType)) {
                Wallet wallet = wallets.get(coinType);
                if (fromValid) {
                    if (wallet.isSetValid()) {
                        wallet.setValid(wallet.getValid() + amount);
                    } else {
                        wallet.setValid(amount);
                    }
                } else {
                    if (wallet.isSetFrozen()) {
                        wallet.setFrozen(wallet.getFrozen() + amount);
                    } else {
                        wallet.setFrozen(amount);
                    }
                }
            } else {
                Wallet wallet = new Wallet();
                wallet.setCoinType(coinType);
                if (fromValid)
                    wallet.setValid(amount);
                else
                    wallet.setFrozen(amount);
                wallets.put(coinType, wallet);
            }
        }
        return true;
    }

    public boolean withdrawal(final long uid, final CoinType coinType, final long amount, final boolean fromValid) {
        if (!users.containsKey(uid)) {
            System.out.println("can't find user to withdrawal");
            return false;
        }
        UserInfo ui = users.get(uid);
        if (!ui.isSetWallets()) {
            System.out.println("the user has no wallet");
            return false;
        }
        Map<CoinType, Wallet> wallets = ui.getWallets();
        if (!wallets.containsKey(coinType)) {
            System.out.println("the use has no wallet with type " + coinType.toString());
            return false;
        }
        Wallet wallet = wallets.get(coinType);
        if (fromValid) {
            if (!wallet.isSetValid() || wallet.getValid() < amount) {
                System.out.println("not enough valid coin");
                return false;
            }
            wallet.setValid(wallet.getValid() - amount);
        } else {
            if (!wallet.isSetFrozen() || wallet.getFrozen() < amount) {
                System.out.println("not enough frozen coin");
                return false;
            }
            wallet.setFrozen(wallet.getFrozen() - amount);
        }

        return true;
    }

    public boolean frozen(final long uid, final CoinType coinType, final long amount) {
        return withdrawal(uid, coinType, amount, true) && deposit(uid, coinType, amount, false);
    }

    public boolean unfreeze(final long uid, final CoinType coinType, final long amount) {
        return withdrawal(uid, coinType, amount, false) && deposit(uid, coinType, amount, true);
    }

    public void placeOrder(OrderInfo oi) {
        TradePair tradePair = oi.getTradePair();
        BlackBoard blackBoard = null;
        if (!blackBoards.containsKey(tradePair)) {
            blackBoard = new BlackBoard(tradePair);
            blackBoards.put(tradePair, blackBoard);
        } else {
            blackBoard = blackBoards.get(tradePair);
        }
        placeOrderInner(blackBoard, oi);
    }

    public boolean cancelOrder(OrderInfo oi) {
        return true;
    }

    public boolean finishOrder(OrderInfo oi) {
        return true;
    }

    private void placeOrderInner(BlackBoard board, OrderInfo oi) {
        // TODO(c): to be finish
        switch (oi.getStrategy()) {
            case NORMAL:
                placeNormalOrder(board, oi);
            case STOP:
                break;
            case TRAILING_STOP:
                break;
            default:
                break;
        }
    }

    private void placeNormalOrder(BlackBoard board, OrderInfo oi) {
        switch (oi.getBos()) {
            case BUY:
                normalBuy(board, oi);
            case SELL:
                normalSell(board, oi);
            default:
                break;
        }
    }

    private void normalBuy(BlackBoard board, OrderInfo oi) {
        /*
        OrderInfo soi = sell.first();
        // TODO(c): optimize this logic
        while (oi.getPrice() >= soi.getPrice() && oi.getQuantity() > 0) {
            int tradeQuantity = java.lang.Math.min(oi.getQuantity(), soi.getQuantity());

            bc.deposit(soi.getUid(), soi.getTo(), tradeQuantity * soi.getPrice(), true);
            bc.withdrawal(soi.getUid(), soi.getFrom(), tradeQuantity, false);
            soi.setQuantity(soi.getQuantity() - tradeQuantity);
            bc.deposit(oi.getUid(), oi.getTo(), tradeQuantity, true);
            bc.withdrawal(oi.getUid(), oi.getFrom(), tradeQuantity * soi.getPrice(), true);
            oi.setQuantity(oi.getQuantity() - tradeQuantity);
            currentPrice = soi.getPrice();

            if (soi.getQuantity() == 0) {
                bc.finishOrder(soi);
                soi = sell.first();
            }
        }
        if (oi.getQuantity() > 0) {
            buy.add(oi);
        } else {
            bc.finishOrder(oi);
        }
        */
    }

    private void normalSell(BlackBoard board, OrderInfo oi) {
    }
}
