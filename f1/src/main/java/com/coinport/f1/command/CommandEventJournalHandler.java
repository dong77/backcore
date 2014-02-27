/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import static org.fusesource.leveldbjni.JniDBFactory.*;

import java.io.*;
import java.util.concurrent.CountDownLatch;

import com.google.common.primitives.Longs;
import com.lmax.disruptor.EventHandler;
import org.iq80.leveldb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;

public final class CommandEventJournalHandler implements EventHandler<CommandEvent> {
    private final static Logger logger = LoggerFactory.getLogger(CommandEventJournalHandler.class);

    // TODO(c) for test
    private long count;
    private CountDownLatch latch = null;

    private DB db;
    private WriteBatch batch;

    public CommandEventJournalHandler() {
        try {
            Options options = new Options();
            options.createIfMissing(true);
            db = factory.open(new File("example"), options);
            batch = db.createWriteBatch();
        } catch (Exception e) {
            logger.error("leveldb error", e);
        }
    }

    public void reset(final CountDownLatch latch, final long expectedCount) {
        this.latch = latch;
        count = expectedCount;
    }

    public void resetMore(final CountDownLatch latch, final long moreCount) {
        reset(latch, count + moreCount);
    }

    @Override
    public void onEvent(final CommandEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        final BPCommand comingCommand = event.getCommand();
        batch.put(Longs.toByteArray(comingCommand.getIndex()), event.getOutput().getBuffer());
        if (endOfBatch) {
            db.write(batch);
            // TODO(c): find a method like clear instead of create a new one
            batch = db.createWriteBatch();
        }

        if (latch != null && count == sequence) {
            latch.countDown();
        }
    }

    // TODO(c): remove this function
    public void closeDb() {
        try {
            db.close();
        } catch (Exception e) {
            logger.error("close leveldb error:", e);
        }
    }
}
