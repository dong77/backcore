/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;

import com.coinport.f1.adapter.*;

public class Subscriber extends JedisPubSub {
    final private static Logger logger = LoggerFactory.getLogger(Subscriber.class);

    private Gson gson;
    private BP bp = new BP();

    public Subscriber() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(BPCommandType.class, new BPCommandTypeAdapter());
        builder.registerTypeAdapter(BOS.class, new BOSAdapter());
        builder.registerTypeAdapter(MOL.class, new MOLAdapter());
        builder.registerTypeAdapter(SOM.class, new SOMAdapter());
        builder.registerTypeAdapter(Strategy.class, new StrategyAdapter());
        builder.registerTypeAdapter(CoinType.class, new CoinTypeAdapter());
        builder.registerTypeAdapter(DOW.class, new DOWAdapter());
        builder.registerTypeAdapter(CommandStats.class, new CommandStatsAdapter());
        gson = builder.create();

        bp.start();
    }

    @Override
    public void onMessage(String channel, String message) {
        logger.debug(message);
        BPCommand command = null;

        try {
            command = gson.fromJson(message, BPCommand.class);
        } catch (JsonSyntaxException e) {
            logger.error("bad json format", e);
        } catch (Exception e) {
            logger.error("exception occur when parse BPCommand", e);
        }
        if (command == null) return;

        logger.debug(command.toString());

        BPCommand nextCommand = bp.nextCommand();
        nextCommand.setType(command.getType());
        switch(command.getType()) {
            case REGISTER_USER:
                nextCommand.setUserInfo(command.getUserInfo());
                break;
            case DW:
                nextCommand.setDwInfo(command.getDwInfo());
                break;
            case PLACE_ORDER:
            case CANCEL_ORDER:
                nextCommand.setOrderInfo(command.getOrderInfo());
                break;
        }
        bp.execute();
    }

    @Override
    public void onPMessage(String pattern, String channel, String message) {
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels) {

    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {

    }

    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {

    }

    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {

    }
}
