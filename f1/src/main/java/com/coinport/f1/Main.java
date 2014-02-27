/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import com.coinport.f1.adapter.*;

public class Main {
    final private static Logger logger = LoggerFactory.getLogger(Main.class);

    private static void subscrib() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();

        JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379, 0);
        final Jedis subscriberJedis = jedisPool.getResource();
        final Subscriber subscriber = new Subscriber();
        try {
            logger.info("listen to the redis message....");
            subscriberJedis.subscribe(subscriber, "command");
        } catch (Exception e) {
        } finally {
            jedisPool.returnResource(subscriberJedis);
        }
    }

    private static void testJson() {
        // String str = "{\"id\":null,\"type\":1,\"userInfo\":{\"id\":null,\"nickname\":\"hoss\",\"password\":\"1234\",\"wallets\":null},\"dwInfo\":null,\"orderInfo\":null,\"timestamp\":null,\"index\":null,\"stats\":3}";
        String str = "{\"id\":null,\"type\":3,\"userInfo\":null,\"dwInfo\":null,\"orderInfo\":{\"id\":null,\"uid\":\"0\",\"tradePair\":{\"from\":\"1000\",\"to\":\"1\"},\"quantity\":\"56\",\"timestamp\":null,\"bos\":\"1\",\"mol\":2,\"som\":1,\"strategy\":1,\"price\":134,\"percentage\":null,\"actPrice\":null,\"expired\":-1,\"routing\":null},\"timestamp\":null,\"index\":null,\"stats\":3}";
        GsonBuilder b = new GsonBuilder();
        b.registerTypeAdapter(BPCommandType.class, new BPCommandTypeAdapter());
        b.registerTypeAdapter(BOS.class, new BOSAdapter());
        b.registerTypeAdapter(MOL.class, new MOLAdapter());
        b.registerTypeAdapter(SOM.class, new SOMAdapter());
        b.registerTypeAdapter(Strategy.class, new StrategyAdapter());
        b.registerTypeAdapter(CoinType.class, new CoinTypeAdapter());
        b.registerTypeAdapter(DOW.class, new DOWAdapter());
        b.registerTypeAdapter(CommandStats.class, new CommandStatsAdapter());
        Gson gson = b.create();

        BPCommand command = gson.fromJson(str, BPCommand.class);
        logger.info(command.toString());
        System.out.println(command.getOrderInfo().isSetPrice());
        System.out.println(command.getOrderInfo().getPrice());
    }

    public static void main (String[] args) {
        subscrib();
        // testJson();
    }
}
