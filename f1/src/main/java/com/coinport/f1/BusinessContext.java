/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BusinessContext {
    private final static Logger logger = LoggerFactory.getLogger(BusinessContext.class);
    private Map<Long, UserInfo> users;
    private Map<TradePair, BlackBoard> blackBoards;

    // Unit test only
    BlackBoard getBlackBoard(final TradePair tp) {
        return blackBoards.get(tp);
    }

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
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("The users' info:");
        for (Map.Entry<Long, UserInfo> entry : users.entrySet()) {
            logger.debug(entry.getKey() + "-->" + entry.getValue().toString());
        }

        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Blackboards' info:");
        for (BlackBoard bb: blackBoards.values()) {
            logger.debug("~~~~~~~~~~~~~");
            bb.display();
        }
    }

    public boolean register(UserInfo ui) {
        users.put(ui.getId(), ui.deepCopy());
        return true;
    }

    public boolean deposit(final long uid, final CoinType coinType, final long amount, final boolean fromValid) {
        if (!users.containsKey(uid)) {
            logger.error("can't find user to deposit");
            return false;
        }
        return deposit(users.get(uid), coinType, amount, fromValid);
    }

    public boolean withdrawal(final long uid, final CoinType coinType, final long amount, final boolean fromValid) {
        if (!users.containsKey(uid)) {
            logger.error("can't find user to withdrawal");
            return false;
        }
        return withdrawal(users.get(uid), coinType, amount, fromValid);
    }

    public boolean frozen(final long uid, final CoinType coinType, final long amount) {
        return withdrawal(uid, coinType, amount, true) && deposit(uid, coinType, amount, false);
    }

    public boolean unfreeze(final long uid, final CoinType coinType, final long amount) {
        return withdrawal(uid, coinType, amount, false) && deposit(uid, coinType, amount, true);
    }

    private boolean frozen(UserInfo ui, final CoinType coinType, final long amount) {
        return withdrawal(ui, coinType, amount, true) && deposit(ui, coinType, amount, false);
    }

    private boolean unfrozen(UserInfo ui, final CoinType coinType, final long amount) {
        return withdrawal(ui, coinType, amount, false) && deposit(ui, coinType, amount, true);
    }

    public boolean placeOrder(OrderInfo oi) {
        TradePair tradePair = oi.getTradePair();
        BlackBoard blackBoard = null;
        if (!blackBoards.containsKey(tradePair)) {
            blackBoard = new BlackBoard(tradePair);
            blackBoards.put(tradePair, blackBoard);
        } else {
            blackBoard = blackBoards.get(tradePair);
        }
        return placeOrderInner(blackBoard, oi);
    }

    public boolean cancelOrder(OrderInfo oi) {
        TradePair tradePair = oi.getTradePair();
        if (!blackBoards.containsKey(tradePair)) {
            return false;
        }
        BlackBoard bb = blackBoards.get(tradePair);
        UserInfo ui = users.get(oi.getUid());
        OrderInfo innerOi = bb.getOrder(oi.getId());
        if (innerOi == null) return false;
        switch (innerOi.getBos()) {
            case BUY:
                bb.eraseFromBuyOrderList(innerOi);
                unfrozen(ui, tradePair.getFrom(), innerOi.getQuantity() * innerOi.getPrice());
                return true;
            case SELL:
                bb.eraseFromSellOrderList(innerOi);
                unfrozen(ui, tradePair.getTo(), innerOi.getQuantity());
                return true;
            default:
                return false;
        }
    }

    private boolean deposit(UserInfo ui, final CoinType coinType, final long amount, final boolean fromValid) {
        if (ui == null) return false;

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

    private boolean withdrawal(UserInfo ui, final CoinType coinType, final long amount, final boolean fromValid) {
        if (ui == null) return false;

        if (!ui.isSetWallets()) {
            logger.error("the user has no wallet");
            return false;
        }
        Map<CoinType, Wallet> wallets = ui.getWallets();
        if (!wallets.containsKey(coinType)) {
            logger.error("the use has no wallet with type " + coinType.toString());
            return false;
        }
        Wallet wallet = wallets.get(coinType);
        if (fromValid) {
            if (!wallet.isSetValid() || wallet.getValid() < amount) {
                logger.error("not enough valid coin");
                return false;
            }
            wallet.setValid(wallet.getValid() - amount);
        } else {
            if (!wallet.isSetFrozen() || wallet.getFrozen() < amount) {
                logger.error("not enough frozen coin");
                return false;
            }
            wallet.setFrozen(wallet.getFrozen() - amount);
        }

        return true;
    }

    private boolean placeOrderInner(BlackBoard board, OrderInfo oi) {
        // TODO(c): to be finish
        switch (oi.getStrategy()) {
            case NORMAL:
                return placeNormalOrder(board, oi);
            case STOP:
                return false;
            case TRAILING_STOP:
                return false;
            default:
                return false;
        }
    }

    private boolean placeNormalOrder(BlackBoard board, OrderInfo oi) {
        switch (oi.getBos()) {
            case BUY:
                return normalBuy(board, oi);
            case SELL:
                return normalSell(board, oi);
            default:
                return false;
        }
    }

    // TODO(c): change always return true
    private boolean normalBuy(BlackBoard board, OrderInfo oi) {
        TradePair tp = oi.getTradePair();
        CoinType from = tp.getFrom();
        CoinType to = tp.getTo();
        int buyQuantity = oi.getQuantity();
        long buyPrice = oi.getPrice();
        long buyAmount =  buyQuantity * buyPrice;
        UserInfo buyer = users.get(oi.getUid());

        // TODO(c): make here more effective
        if (buyer == null || buyer.getWallets() == null || !buyer.getWallets().containsKey(from)) {
            logger.info("null in get path");
            return true;
        }

        if (buyAmount > buyer.getWallets().get(from).getValid()) {
            logger.info("not enough money");
            return true;
        } else {
            frozen(buyer, from, buyAmount);
        }

        OrderInfo soi = board.getFirstSellOrder();
        if (soi == null) {
            board.putToBuyOrderList(oi);
            return true;
        }
        long sellPrice = soi.getPrice();
        while (buyPrice >= sellPrice && buyQuantity > 0) {
            int sellQuantity = soi.getQuantity();
            UserInfo seller = users.get(soi.getUid());

            int tradeQuantity = java.lang.Math.min(buyQuantity, sellQuantity);
            long sellAmount = tradeQuantity * sellPrice;


            deposit(seller, from, sellAmount, true);
            withdrawal(seller, to, tradeQuantity, false);
            sellQuantity -= tradeQuantity;

            deposit(buyer, to, tradeQuantity, true);
            withdrawal(buyer, from, sellAmount, false);
            buyQuantity -= tradeQuantity;

            board.priceChanged(sellPrice);


            if (sellQuantity == 0) {
                board.eraseFromSellOrderList(soi);

                soi = board.getFirstSellOrder();
                if (soi == null)
                    break;
                sellPrice = soi.getPrice();
            } else {
                soi.setQuantity(sellQuantity);
            }
        }
        if (buyQuantity > 0) {
            oi.setQuantity(buyQuantity);
            board.putToBuyOrderList(oi);
        }
        return true;
    }

    // TODO(c): Merge this logical with normalBuy function.
    // TODO(c): change always return true
    private boolean normalSell(BlackBoard board, OrderInfo oi) {
        TradePair tp = oi.getTradePair();
        CoinType from = tp.getFrom();
        CoinType to = tp.getTo();
        int sellQuantity = oi.getQuantity();
        long sellPrice = oi.getPrice();
        UserInfo seller = users.get(oi.getUid());

        if (sellQuantity > seller.getWallets().get(from).getValid()) {
            logger.info("not enough money");
            return true;
        } else {
            frozen(seller, to, sellQuantity);
        }

        OrderInfo boi = board.getFirstBuyOrder();
        if (boi == null) {
            board.putToSellOrderList(oi);
            return true;
        }
        long buyPrice = boi.getPrice();
        while (sellPrice <= buyPrice && sellQuantity > 0) {
            int buyQuantity = boi.getQuantity();
            UserInfo buyer = users.get(boi.getUid());

            int tradeQuantity = java.lang.Math.min(sellQuantity, buyQuantity);
            long buyCost = tradeQuantity * buyPrice;


            deposit(buyer, to, tradeQuantity, true);
            withdrawal(buyer, from, buyCost, false);
            buyQuantity -= tradeQuantity;

            deposit(seller, from, buyCost, true);
            withdrawal(seller, to, tradeQuantity, false);
            sellQuantity -= tradeQuantity;

            board.priceChanged(buyPrice);


            if (buyQuantity == 0) {
                board.eraseFromBuyOrderList(boi);

                boi = board.getFirstBuyOrder();
                if (boi == null)
                    break;
                buyPrice = boi.getPrice();
            } else {
                boi.setQuantity(buyQuantity);
            }
        }
        if (sellQuantity > 0) {
            oi.setQuantity(sellQuantity);
            board.putToSellOrderList(oi);
        }
        return true;
    }
}
