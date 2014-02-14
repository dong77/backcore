/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackBoard {
    private final static Logger logger = LoggerFactory.getLogger(BlackBoard.class);

    private TradePair tradePair;
    private long currentPrice = -1;
    private final TreeSet<OrderInfo> buyList = new TreeSet<OrderInfo>(new OrderComparator(false));
    private final TreeSet<OrderInfo> sellList = new TreeSet<OrderInfo>(new OrderComparator(true));
    private final HashSet<OrderInfo> conditionOrderList = new HashSet<OrderInfo>();  // not use yet

    public BlackBoard(TradePair tp) {
        tradePair = tp;
    }

    public OrderInfo getFirstBuyOrder() {
        if (buyList.isEmpty())
            return null;
        return buyList.first();
    }

    public OrderInfo getFirstSellOrder() {
        if (sellList.isEmpty())
            return null;
        return sellList.first();
    }

    public void putToBuyOrderList(OrderInfo oi) {
        buyList.add(oi);
    }

    public void putToSellOrderList(OrderInfo oi) {
        sellList.add(oi);
    }

    public void eraseFromBuyOrderList(OrderInfo oi) {
        buyList.remove(oi);
    }

    public void eraseFromSellOrderList(OrderInfo oi) {
        sellList.remove(oi);
    }

    public void priceChanged(final long price) {
        currentPrice = price;
    }

    public void display() {
        logger.debug("Trade pair: " + tradePair.toString());
        logger.debug("Current price: " + currentPrice);
        logger.debug("Buy list:");
        displaySet(buyList);
        logger.debug("Sell list:");
        displaySet(sellList);
    }

    private void displaySet(final TreeSet<OrderInfo> orderInfos) {
        for (OrderInfo oi : orderInfos) {
            logger.debug(oi.toString());
        }
    }
}
