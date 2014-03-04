/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1.perftest;

import static org.fusesource.leveldbjni.JniDBFactory.*;

import java.io.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

import ch.qos.logback.classic.LoggerContext;
import com.google.common.primitives.Longs;
import org.iq80.leveldb.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.serializers.FieldSerializer;

import com.coinport.f1.*;

public class Main {
    final static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        perfTest();
        // treeSetTest();
        leveldbjniTest();
        // testLog();
    }

    private static void showTreeSet(final Set<OrderInfo> ts) {
        for (OrderInfo entry : ts) {
            logger.debug(entry.toString());
        }
    }

    private static OrderInfo addOrderInfo(
        final long id, final long uid, final int quantity, final long ts, final long price) {
        OrderInfo oi = new OrderInfo();
        oi.setId(id);
        oi.setUid(uid);
        oi.setQuantity(quantity);
        oi.setTimestamp(ts);
        oi.setPrice(price);
        return oi;
    }

    private static void perfTest() throws Exception {
        long userNum = 1000L * 200L;
        // long userNum = 4;

        PerfTest pt = new PerfTest();

        pt.test(0, userNum);
        pt.test(1, userNum * 10L);
        pt.test(2, userNum * 10L);

        // pt.display();
        pt.terminate();
    }

    private static void testLog() {
        logger.debug("this is a debug log test");
        logger.error("this is a error log test");

        // important!
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.stop();

    }

    private static void treeSetTest() {
        TreeSet<OrderInfo> ts = new TreeSet<OrderInfo>(new OrderComparator(false));
        ts.add(addOrderInfo(2, 1001, 3, 1000001, 90));
        ts.add(addOrderInfo(3, 1001, 3, 1000003, 90));
        ts.add(addOrderInfo(2, 1001, 3, 1000003, 90));
        ts.add(addOrderInfo(2, 1001, 3, 1000003, 83));
        ts.add(addOrderInfo(2, 1001, 3, 1000025, 34));
        ts.add(addOrderInfo(2, 1001, 3, 1000041, 69));
        ts.add(addOrderInfo(2, 1001, 3, 1000011, 17));
        ts.add(addOrderInfo(2, 1001, 3, 1000003, 40));
        ts.add(addOrderInfo(2, 1001, 3, 1000005, 40));
        ts.add(addOrderInfo(1, 1001, 3, 1000009, 100));
        ts.add(addOrderInfo(1, 1001, 4, 1000009, 100));
        ts.add(addOrderInfo(1, 1001, 4, 1000009, 100));
        showTreeSet(ts);

        OrderInfo oi = ts.first();
        ts.remove(oi);
        showTreeSet(ts);
    }

    private static void leveldbjniTest() throws Exception {
        Kryo kryo = new Kryo();
        FieldSerializer<?> serializer = new FieldSerializer<BPCommand>(kryo, BPCommand.class);
        kryo.register(BPCommand.class, serializer);

        DB db = null;
        DBIterator iterator = null;
        try {
            Options options = new Options();
            options.createIfMissing(true);
            db = factory.open(new File("leveldb/command"), options);
            // db.put(bytes("Tampa"), bytes("rocks"));
            // iterator = db.iterator();
            // for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                // System.out.println(Longs.fromByteArray(iterator.peekNext().getKey()));
            // }
            byte[] content = db.get(Longs.toByteArray(220000L));
            BPCommand command = kryo.readObject(new Input(content), BPCommand.class);
            logger.info(command.toString());
            // Use the db in here....
        } catch (Exception e) {
            logger.error("error!", e);
        } finally {
            // Make sure you close the db to shutdown the
            // database and avoid resource leaks.
            db.close();
        }
    }
}
