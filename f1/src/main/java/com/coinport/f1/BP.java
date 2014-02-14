/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.YieldingWaitStrategy;

import com.coinport.f1.command.CommandEvent;
import com.coinport.f1.command.CommandEventJournalHandler;
import com.coinport.f1.command.CommandEventProcessHandler;
import com.coinport.f1.command.CommandEventReplicateHandler;

/**
 * <pre>
 *
 * Pipeline a series of stages from a publisher to ultimate event processor.
 * Each event processor depends on the output of the event processor.
 *
 * +----+    +-----+    +-----+    +-----+
 * | P1 |--->| EP1 |--->| EP2 |--->| EP3 |
 * +----+    +-----+    +-----+    +-----+
 *
 *
 * Disruptor:
 * ==========
 *                           track to prevent wrap
 *              +----------------------------------------------------------------+
 *              |                                                                |
 *              |                                                                v
 * +----+    +====+    +=====+    +-----+    +=====+    +-----+    +=====+    +-----+
 * | P1 |--->| RB |    | SB1 |<---| EP1 |<---| SB2 |<---| EP2 |<---| SB3 |<---| EP3 |
 * +----+    +====+    +=====+    +-----+    +=====+    +-----+    +=====+    +-----+
 *      claim   ^  get    |   waitFor           |   waitFor           |  waitFor
 *              |         |                     |                     |
 *              +---------+---------------------+---------------------+
 *        </pre>
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * SB1 - SequenceBarrier 1
 * EP1 - EventProcessor 1
 * SB2 - SequenceBarrier 2
 * EP2 - EventProcessor 2
 * SB3 - SequenceBarrier 3
 * EP3 - EventProcessor 3
 *
 * </pre>
 *
 * TODO(c) make journaler and replicator paral to enfast the BP:
 * <pre>
 *
 *                       +-----+
 *                +----->| EP2 |
 *                |      +-----+
 *                |
 * +----+      +-----+
 * | P1 |----->| EP1 |
 * +----+      +-----+
 *                |
 *                |      +-----+
 *                +----->| EP3 |
 *                       +-----+
 *
 * </pre>
 */
public final class BP {
    private final static Logger logger = LoggerFactory.getLogger(BP.class);

    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int NUM_NEEDED_PROCESSORS = NUM_EVENT_PROCESSORS + 1;  // adds publisher

    // The size of the ring buffer. Best practice is 2^x and meet the size of CPU's L3 cache.
    // Tips: lscpu to show the L3 cache size in Ubuntu.
    private static final int BUFFER_SIZE =  1 << 20;

    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS);

    private final RingBuffer<CommandEvent> ringBuffer =
        createSingleProducer(CommandEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());
    private final SequenceBarrier logicBarrier = ringBuffer.newBarrier();
    private final BusinessContext bc = new BusinessContext();
    private final CommandEventProcessHandler logicHandler = new CommandEventProcessHandler(bc);
    private final BatchEventProcessor<CommandEvent> logicProcessor =
        new BatchEventProcessor<CommandEvent>(ringBuffer, logicBarrier, logicHandler);

    private final SequenceBarrier journalBarrier = ringBuffer.newBarrier(logicProcessor.getSequence());
    private final CommandEventJournalHandler journalHandler = new CommandEventJournalHandler();
    private final BatchEventProcessor<CommandEvent> journalProcessor =
        new BatchEventProcessor<CommandEvent>(ringBuffer, journalBarrier, journalHandler);

    private final SequenceBarrier replicateBarrier = ringBuffer.newBarrier(journalProcessor.getSequence());
    private final CommandEventReplicateHandler replicateHandler = new CommandEventReplicateHandler();
    private final BatchEventProcessor<CommandEvent> replicateProcessor =
        new BatchEventProcessor<CommandEvent>(ringBuffer, replicateBarrier, replicateHandler);
    {
        ringBuffer.addGatingSequences(replicateProcessor.getSequence());
    }

    private long sequence = 0;

    public void BP() {
        final int availableProcessors = Runtime.getRuntime().availableProcessors();
        if (NUM_NEEDED_PROCESSORS > availableProcessors)
        {
            // TODO(c) use logback
            logger.warn(
                "*** Warning ***: your system has insufficient processors to execute the test efficiently. ");
            logger.warn(
                "Processors required = " + NUM_NEEDED_PROCESSORS + " available = " + availableProcessors);
        }
    }

    public void start() {
        executor.submit(logicProcessor);
        executor.submit(journalProcessor);
        executor.submit(replicateProcessor);
    }

    public void terminate() {
        logicProcessor.halt();
        journalProcessor.halt();
        replicateProcessor.halt();
        executor.shutdown();
    }

    public CommandEvent nextCommand() {
        sequence = ringBuffer.next();
        return ringBuffer.get(sequence);
    }

    public void execute() {
        ringBuffer.publish(sequence);
    }

    public void setStopParams(final CountDownLatch latch, final long expectedCount) {
        replicateHandler.reset(latch, replicateProcessor.getSequence().get() + expectedCount);
    }

    public void setMore(final CountDownLatch latch, final long expectedCount) {
        replicateHandler.resetMore(latch, expectedCount);
    }

    public void displayBC() {
        bc.display();
    }

    public BusinessContext getContext() {
        return bc;
    }
}
