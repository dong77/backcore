/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.output_event;

import com.esotericsoftware.kryo.io.Output;
import com.lmax.disruptor.EventFactory;

import com.coinport.f1.OutputEventImpl;

public final class OutputEvent {
    private final OutputEventImpl eventImpl = new OutputEventImpl();

    public OutputEventImpl getOutputEventImpl() {
        return eventImpl;
    }

    public static final EventFactory<OutputEvent> EVENT_FACTORY = new EventFactory<OutputEvent>()
    {
        public OutputEvent newInstance()
        {
            return new OutputEvent();
        }
    };
}
