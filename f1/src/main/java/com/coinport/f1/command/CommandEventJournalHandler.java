/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

// import java.io.ByteArrayOutputStream;
import static org.fusesource.leveldbjni.JniDBFactory.*;

import java.io.*;

import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.google.common.primitives.Longs;
import com.lmax.disruptor.EventHandler;
import org.iq80.leveldb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;


public final class CommandEventJournalHandler implements EventHandler<CommandEvent> {
    private final static Logger logger = LoggerFactory.getLogger(CommandEventJournalHandler.class);
    private DB db;
    private WriteBatch batch;
    private BPCommand command;
    // private ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    private Output output = new Output(128, 1024);

    private Kryo kryo;

    // public CommandEventJournalHandler(DB db) {
    public CommandEventJournalHandler() {
        /*
        this.db = db;
        */
        try {
            Options options = new Options();
            options.createIfMissing(true);
            db = factory.open(new File("example"), options);
            batch = db.createWriteBatch();
        } catch (Exception e) {
            logger.error("leveldb error", e);
        }

        command = new BPCommand();

        kryo = new Kryo();
        FieldSerializer<?> serializer = new FieldSerializer<BPCommand>(kryo, BPCommand.class);
        kryo.register(BPCommand.class, serializer);
    }

    @Override
    public void onEvent(final CommandEvent event, final long sequence, final boolean endOfBatch) throws Exception {
        final BPCommand comingCommand = event.getCommand();
        command.setId(comingCommand.getId());
        command.setStats(comingCommand.getStats());

        kryo.writeObject(output, command);
        batch.put(Longs.toByteArray(comingCommand.getIndex()), output.getBuffer());
        output.clear();
        if (endOfBatch) {
            db.write(batch);
            // TODO(c): find a method like clear instead of create a new one
            batch = db.createWriteBatch();
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
