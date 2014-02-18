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
        command.clear();
        final BPCommand comingCommand = event.getCommand();
        command.setType(comingCommand.getType());
        command.setTimestamp(comingCommand.getTimestamp());
        command.setIndex(comingCommand.getIndex());
        switch (command.getType()) {
            case REGISTER_USER:
                command.setUserInfo(comingCommand.getUserInfo());
                break;
            case DW:
                command.setDwInfo(comingCommand.getDwInfo());
                break;
            case PLACE_ORDER:
            case CANCEL_ORDER:
                command.setOrderInfo(comingCommand.getOrderInfo());
                break;
            default:
                break;
        }
        // command.toString();
        kryo.writeObject(output, command);
        output.flush();
        // db.put(Longs.toByteArray(command.getIndex()), output.getBuffer());
        batch.put(Longs.toByteArray(command.getIndex()), output.getBuffer());
        output.clear();
        if (endOfBatch) {
            db.write(batch);
            // TODO(c): find a method like clear instead of create a new one
            batch = db.createWriteBatch();
            // batch.clear();
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
