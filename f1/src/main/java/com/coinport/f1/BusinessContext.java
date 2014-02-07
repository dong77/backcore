/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashMap;
import java.util.Map;

public class BusinessContext {
    private Map<Long, UserInfo> users;

    public UserInfo getUser(long uid) {
        return users.get(uid);
    }

    public long userNum() {
        return users.size();
    }

    public BusinessContext() {
        users = new HashMap<Long, UserInfo>();
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

    public boolean placeOrder(OrderInfo oi) {
        return true;
    }

    public boolean cancelOrder(OrderInfo oi) {
        return true;
    }
}
