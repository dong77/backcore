/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class BlackBoardZone {
    private long currentPrice = -1;
    private final TreeSet<OrderInfo> buy = new TreeSet<OrderInfo>(new OrderComparator(false));
    private final TreeSet<OrderInfo> sell = new TreeSet<OrderInfo>(new OrderComparator(true));
    private final HashSet<OrderInfo> conditionOrder = new HashSet<OrderInfo>();

    public boolean placeOrder(OrderInfo oi) {
        switch (oi.getStrategy()) {
            case NORMAL:
                return placeNormalOrder(oi);
            case STOP:
                return true;
            case TRAILING_STOP:
                return true;
            default:
                return false;
        }
    }

    private boolean placeNormalOrder(OrderInfo oi) {
        switch (oi.getBos()) {
            case BUY:
                return buyOrder(oi);
            case SELL:
                return sellOrder(oi);
            default:
                return false;
        }
    }

    private boolean buyOrder(OrderInfo oi) {
        return true;
    }

    private boolean sellOrder(OrderInfo oi) {
        return true;
    }
}
