/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.output_event;

import static org.fusesource.lmdbjni.Constants.*;

import java.io.*;
import java.util.concurrent.CountDownLatch;

import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.google.common.primitives.Longs;
import com.lmax.disruptor.EventHandler;
import org.fusesource.lmdbjni.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;

public final class OutputEventHandler implements EventHandler<OutputEvent> {
    private final static Logger logger = LoggerFactory.getLogger(OutputEventHandler.class);

    private long calledNum = 0;
    private Env env = new Env();
    private Database db = null;

    private OutputEventImpl eventImpl;
    private Kryo kryo;
    private Output output = new Output(128, 1024);

    public OutputEventHandler() {
        eventImpl = new OutputEventImpl();

        kryo = new Kryo();
        FieldSerializer<?> serializer = new FieldSerializer<OutputEventImpl>(kryo, OutputEventImpl.class);
        kryo.register(OutputEventImpl.class, serializer);

        try {
            File dbdir = new File("lmdb/output");
            File parent = dbdir.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            env.open("lmdb");
            db = env.openDatabase("output");
        } catch (Exception e) {
            logger.error("can't get lmdb instance", e);
            db.close();
            env.close();
        }
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

        db.put(Longs.toByteArray(eventImpl.getIndex()), output.getBuffer());
    }

    // TODO(c): remove this function
    public void closeDb() {
        logger.info("called times: " + calledNum);
        // db.close();
        // env.close();
    }
}
