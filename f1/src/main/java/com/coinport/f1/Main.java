/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class Main {
    final private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main (String[] args) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();

        JedisPool jedisPool = new JedisPool(poolConfig, "localhost", 6379, 0);
        final Jedis subscriberJedis = jedisPool.getResource();
        final Subscriber subscriber = new Subscriber();
        try {
            subscriberJedis.subscribe(subscriber, "command");
        } catch (Exception e) {
        } finally {
            jedisPool.returnResource(subscriberJedis);
        }

        // Jedis jedis = new Jedis("localhost");
        // jedis.set("jedis", "hello jedis");
        // String value = jedis.get("jedis");
        // logger.info(value);
        // try {
            // JRedis redis = new JRedisClient("*.*.*.*",6379);
            // String key = "mKey";
            // redis.set(key, "hello redis");
            // String v = new String(redis.get(key));
            // String k2 = "count";
            // jr.incr(k2);
            // jr.incr(k2);
            // logger.info(v);
            // logger.info(new String(redis.get(k2)));
        // } catch (Exception e) {
            // logger.error("error occur :", e);
        // }
    }
}
