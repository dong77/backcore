/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coinport.f1.command.CommandEvent;
import com.coinport.f1.command.CommandEventJournalHandler;
import com.coinport.f1.command.CommandEventProcessHandler;
import com.coinport.f1.command.CommandEventSerializeHandler;

import com.coinport.f1.config.ConfigLoader;

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
 * TODO(c) make serializer and journaler paral to enfast the BP:
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

    private static final int NUM_EVENT_PROCESSORS = 5;
    private static final int NUM_NEEDED_PROCESSORS = NUM_EVENT_PROCESSORS + 1;  // adds publisher

    private static final int BUFFER_SIZE = ConfigLoader.getConfig().bufferSize;

    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS);

    private final RingBuffer<CommandEvent> ringBuffer =
        createSingleProducer(CommandEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());
    private final SequenceBarrier logicBarrier = ringBuffer.newBarrier();
    private final Trader trader = new Trader();
    private final CommandEventProcessHandler logicHandler = new CommandEventProcessHandler(trader);
    private final BatchEventProcessor<CommandEvent> logicProcessor =
        new BatchEventProcessor<CommandEvent>(ringBuffer, logicBarrier, logicHandler);

    private final SequenceBarrier serializeBarrier = ringBuffer.newBarrier(logicProcessor.getSequence());
    private final CommandEventSerializeHandler serializeHandler = new CommandEventSerializeHandler();
    private final BatchEventProcessor<CommandEvent> serializeProcessor =
        new BatchEventProcessor<CommandEvent>(ringBuffer, serializeBarrier, serializeHandler);

    private final SequenceBarrier journalBarrier = ringBuffer.newBarrier(serializeProcessor.getSequence());
    private final CommandEventJournalHandler journalHandler = new CommandEventJournalHandler();
    private final BatchEventProcessor<CommandEvent> journalProcessor =
        new BatchEventProcessor<CommandEvent>(ringBuffer, journalBarrier, journalHandler);
    {
        ringBuffer.addGatingSequences(journalProcessor.getSequence());
    }

    private long sequence = 0;

    public BP() {
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
        executor.submit(serializeProcessor);
        executor.submit(journalProcessor);
        trader.start();
    }

    public void terminate() {
        logicProcessor.halt();
        serializeProcessor.halt();
        journalProcessor.halt();
        // TODO(c): put db in this class and pass to journalProcessor from cons
        journalHandler.closeDb();
        trader.terminate();
        executor.shutdown();
    }

    public OrderInfo nextPlaceOrder() {
        BPCommand bpc = nextCommand();

        bpc.setType(BPCommandType.PLACE_ORDER);

        OrderInfo oi = null;
        if (!bpc.isSetOrderInfo()) {
            oi = new OrderInfo();
            bpc.setOrderInfo(oi);
        } else {
            oi = bpc.getOrderInfo();
            oi.clear();
        }
        return oi;
    }

    public OrderInfo nextCancelOrder() {
        BPCommand bpc = nextCommand();

        bpc.setType(BPCommandType.CANCEL_ORDER);

        OrderInfo oi = null;
        if (!bpc.isSetOrderInfo()) {
            oi = new OrderInfo();
            bpc.setOrderInfo(oi);
        } else {
            oi = bpc.getOrderInfo();
            oi.clear();
        }
        return oi;
    }

    public DWInfo nextDepositWithdrawal() {
        BPCommand bpc = nextCommand();

        bpc.setType(BPCommandType.DW);

        DWInfo dwi = null;
        if (!bpc.isSetDwInfo()) {
            dwi = new DWInfo();
            bpc.setDwInfo(dwi);
        } else {
            dwi = bpc.getDwInfo();
            dwi.clear();
        }
        return dwi;
    }

    public UserInfo nextRegisterUser() {
        BPCommand bpc = nextCommand();

        bpc.setType(BPCommandType.REGISTER_USER);

        UserInfo ui = null;
        if (!bpc.isSetUserInfo()) {
            ui = new UserInfo();
            bpc.setUserInfo(ui);
        } else {
            ui = bpc.getUserInfo();
            ui.clear();
        }
        return ui;
    }

    public void execute() {
        ringBuffer.publish(sequence);
    }

    public void setStopParams(final CountDownLatch latch, final long expectedCount) {
        journalHandler.reset(latch, journalProcessor.getSequence().get() + expectedCount);
        trader.setStopParams(latch, expectedCount);
    }

    public void setMore(final CountDownLatch latch, final long expectedCount) {
        journalHandler.resetMore(latch, expectedCount);
        trader.setMore(latch, expectedCount);
    }

    public void displayBC() {
        trader.display();
    }

    public Trader getTrader() {
        return trader;
    }

    public BPCommand nextCommand() {
        sequence = ringBuffer.next();
        BPCommand bpc = ringBuffer.get(sequence).getCommand();

        // TODO(c): sets these fields outter of BP for performance concern.
        bpc.setIndex(sequence);
        bpc.setTimestamp(System.currentTimeMillis());

        return bpc;
    }
}
