/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.lmax.disruptor.EventHandler;

import java.util.concurrent.CountDownLatch;

public final class CommandEventReplicateHandler implements EventHandler<CommandEvent> {
    // TODO(c) for test
    private long count;
    private CountDownLatch latch = null;

    public void reset(final CountDownLatch latch, final long expectedCount) {
        this.latch = latch;
        count = expectedCount;
    }

    public void resetMore(final CountDownLatch latch, final long moreCount) {
        reset(latch, count + moreCount);
    }

    @Override
    public void onEvent(final CommandEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        if (latch != null && count == sequence) {
            latch.countDown();
        }
    }
}
