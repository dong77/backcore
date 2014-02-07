/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.coinport.f1.BPCommand;

import com.coinport.f1.BusinessContext;
import com.coinport.f1.OrderInfo;

public class CancelOrderHandler extends CommandHandler {
    @Override
    public void exec(final BPCommand command, BusinessContext bc) {
        if (command.isSetOrderInfo()) {
            bc.cancelOrder(command.getOrderInfo());
        } else {
            System.out.println("no orderInfo found in cancel order command");
        }
    }
}
