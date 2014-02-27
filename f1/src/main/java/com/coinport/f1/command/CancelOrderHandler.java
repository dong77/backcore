/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;

import com.coinport.f1.Trader;
import com.coinport.f1.OrderInfo;

public class CancelOrderHandler extends CommandHandler {
    @Override
    public boolean exec(final BPCommand command, Trader trader) {
        if (command.isSetOrderInfo()) {
            return trader.cancelOrder(command.getIndex(), command.getOrderInfo());
        } else {
            logger.error("no orderInfo found in cancel order command");
            return false;
        }
    }
}
