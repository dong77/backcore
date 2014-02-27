/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.*;

public final class CommandEventSerializeHandler implements EventHandler<CommandEvent> {
    private final static Logger logger = LoggerFactory.getLogger(CommandEventSerializeHandler.class);
    private BPCommand command;
    private Kryo kryo;

    public CommandEventSerializeHandler() {
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

        Output output = event.getOutput();
        output.clear();
        kryo.writeObject(output, command);
    }
}
