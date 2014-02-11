/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import java.util.Comparator;

public class OrderComparator implements Comparator<OrderInfo> {
    private boolean isAscend = true;

    public OrderComparator(boolean isAscend) {
        this.isAscend = isAscend;
    }

    @Override
    public int compare(final OrderInfo lhs, final OrderInfo rhs) {
        if (isAscend) {
            return (int)compareInner(lhs, rhs);
        } else {
            return -(int)compareInner(lhs, rhs);
        }
    }

    private long compareInner(final OrderInfo lhs, final OrderInfo rhs) {
        if (lhs.isSetPrice() && rhs.isSetPrice()) {
            long priceDiff = lhs.getPrice() - rhs.getPrice();
            if (priceDiff != 0) {
                return priceDiff;
            }
        }
        if (lhs.isSetTimestamp() && rhs.isSetTimestamp()) {
            long tsDiff = lhs.getTimestamp() - rhs.getTimestamp();
            if (tsDiff != 0) {
                return tsDiff;
            }
        }
        long idDiff = lhs.getId() - rhs.getId();
        if (idDiff != 0) return idDiff;

        // adds these code for save. in fact, these code should never be ran
        return lhs.compareTo(rhs);
    }
}
