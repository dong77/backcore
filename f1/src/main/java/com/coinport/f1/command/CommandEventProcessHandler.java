/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import com.lmax.disruptor.EventHandler;

import com.coinport.f1.BPCommand;
import com.coinport.f1.BPCommandType;
import com.coinport.f1.BusinessContext;

public final class CommandEventProcessHandler implements EventHandler<CommandEvent> {
    private BusinessContext bc;

    private static Map<BPCommandType, CommandHandler> handlers = new HashMap<BPCommandType, CommandHandler>();
    static {
        handlers.put(BPCommandType.REGISTER_USER, new RegisterUserHandler());
        handlers.put(BPCommandType.DW, new DWHandler());
        handlers.put(BPCommandType.PLACE_ORDER, new PlaceOrderHandler());
        handlers.put(BPCommandType.CANCEL_ORDER, new CancelOrderHandler());
    }

    public CommandEventProcessHandler(BusinessContext bc) {
        this.bc = bc;
    }

    @Override
    public void onEvent(final CommandEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        BPCommand command = event.getCommand();
        handlers.get(command.getType()).exec(command, bc);
    }
}
