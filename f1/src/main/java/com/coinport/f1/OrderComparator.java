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
        if (lhs.isSetPrice() && rhs.isSetPrice()) {
            int priceDiff = (int)(lhs.getPrice() - rhs.getPrice());
            if (priceDiff != 0) {
                return isAscend ? priceDiff : -priceDiff;
            }
        }

        // late order will put to the behind of the earlier order.
        if (lhs.isSetTimestamp() && rhs.isSetTimestamp()) {
            int tsDiff = (int)(lhs.getTimestamp() - rhs.getTimestamp());
            if (tsDiff != 0) {
                return tsDiff;
            }
        }

        return lhs.compareTo(rhs);
    }
}
