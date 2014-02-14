/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.lmax.disruptor.EventHandler;

import java.util.concurrent.CountDownLatch;

public final class CommandEventJournalHandler implements EventHandler<CommandEvent> {
    @Override
    public void onEvent(final CommandEvent event, final long sequence, final boolean endOfBatch) throws Exception {
    }
}
