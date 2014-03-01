/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.output_event;

import static org.fusesource.leveldbjni.JniDBFactory.*;
import static org.fusesource.lmdbjni.Constants.*;

import java.io.*;
import java.util.concurrent.CountDownLatch;

import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.google.common.primitives.Longs;
import com.lmax.disruptor.EventHandler;
import org.fusesource.lmdbjni.*;
import org.iq80.leveldb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;

public final class OutputEventHandler implements EventHandler<OutputEvent> {
    private final static Logger logger = LoggerFactory.getLogger(OutputEventHandler.class);

    // TODO(c) for test
    private long count;
    private CountDownLatch latch = null;

    private DB db;
    private WriteBatch batch;

    private long calledNum = 0;

    private OutputEventImpl eventImpl;
    private Kryo kryo;
    private Output output = new Output(128, 1024);

    public OutputEventHandler() {
        eventImpl = new OutputEventImpl();

        kryo = new Kryo();
        FieldSerializer<?> serializer = new FieldSerializer<OutputEventImpl>(kryo, OutputEventImpl.class);
        kryo.register(OutputEventImpl.class, serializer);

        try {
            Options options = new Options();
            options.createIfMissing(true);
            File dbdir = new File("leveldb/output");
            File parent = dbdir.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            db = factory.open(dbdir, options);
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
    public void onEvent(final OutputEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        ++calledNum;
        eventImpl.clear();
        final OutputEventImpl outputEvent = event.getOutputEventImpl();
        eventImpl.setIndex(outputEvent.getIndex());
        eventImpl.setType(outputEvent.getType());

        output.clear();
        kryo.writeObject(output, eventImpl);

        batch.put(Longs.toByteArray(eventImpl.getIndex()), output.getBuffer());
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
        logger.info("called times: " + calledNum);
        try {
            db.close();
        } catch (Exception e) {
            logger.error("close leveldb error:", e);
        }
    }
}
