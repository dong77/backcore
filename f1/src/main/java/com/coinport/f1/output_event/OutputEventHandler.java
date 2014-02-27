/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.output_event;

import java.io.*;
import java.util.concurrent.CountDownLatch;

import com.google.common.primitives.Longs;
import com.lmax.disruptor.EventHandler;
import com.mongodb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;

public final class OutputEventHandler implements EventHandler<OutputEvent> {
    private final static Logger logger = LoggerFactory.getLogger(OutputEventHandler.class);

    private long calledNum = 0;
    DBCollection coll = null;

    public OutputEventHandler() {
        try {
            MongoClient mongoClient = new MongoClient("localhost", 27017);
            DB db = mongoClient.getDB("output");
            coll = db.getCollection("event");
        } catch (Exception e) {
            logger.error("can't get mongodb instance", e);
        }
    }

    @Override
    public void onEvent(final OutputEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        ++calledNum;
        if (coll == null) return;

        final OutputEventImpl outputEvent = event.getOutputEventImpl();
        BasicDBObject obj = new BasicDBObject("_id", outputEvent.getIndex());

        OutputEventType type = outputEvent.getType();
        obj.append("t", type.getValue());
        coll.save(obj);
    }

    // TODO(c): remove this function
    public void closeDb() {
        logger.info("called times: " + calledNum);
    }
}
