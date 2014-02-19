/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;

import com.coinport.f1.BusinessContext;
import com.coinport.f1.OrderInfo;

public class PlaceOrderHandler extends CommandHandler {
    @Override
    public boolean exec(final BPCommand command, BusinessContext bc) {
        if (command.isSetOrderInfo()) {
            return bc.placeOrder(command.getOrderInfo());
        } else {
            logger.error("no orderInfo found in place order command");
            return false;
        }
    }
}
