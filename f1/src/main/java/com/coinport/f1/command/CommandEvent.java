/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.command;

import com.lmax.disruptor.EventFactory;

import com.coinport.f1.BPCommand;

public final class CommandEvent {
    private final BPCommand command = new BPCommand();

    public BPCommand getCommand() {
        return command;
    }

    public static final EventFactory<CommandEvent> EVENT_FACTORY = new EventFactory<CommandEvent>()
    {
        public CommandEvent newInstance()
        {
            return new CommandEvent();
        }
    };
}
