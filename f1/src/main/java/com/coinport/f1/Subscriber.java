/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

package com.coinport.f1;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.MultiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;

import com.coinport.f1.adapter.*;

public class Subscriber extends JedisPubSub {
    final private static Logger logger = LoggerFactory.getLogger(Subscriber.class);

    private static final String AMOUNT = "a";
    private static final String BUY_OR_SELL = "b";
    private static final String COIN = "c";
    private static final String COMMAND_TYPE = "ct";
    private static final String DW = "d";
    private static final String FROM = "f";
    private static final String ID = "i";
    private static final String MARKET_OR_LIMIT = "m";
    private static final String NAME = "n";
    private static final String PRICE = "p";
    private static final String PASSWORD = "pw";
    private static final String QUANTITY = "q";
    private static final String SHORT_OR_MORE = "s";
    private static final String STRATEGY = "st";
    private static final String TO = "t";
    private static final String UID = "u";

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

    private boolean createRegisterUserCommand(MultiMap<String> values) {
        String name = values.getString(NAME),
            pw = values.getString(PASSWORD);
        if (name == null || pw == null)
            return false;
        UserInfo ui = bp.nextRegisterUser();
        ui.setNickname(name);
        ui.setPassword(pw);

        String uid = values.getString(UID);
        if (uid != null) {
            ui.setId(Long.parseLong(uid));
        }
        return true;
    }

    private boolean createDwCommand(MultiMap<String> values) {
        String uid = values.getString(UID),
            coinType = values.getString(COIN),
            amount = values.getString(AMOUNT);
        if (uid == null || coinType == null || amount == null) {
            return false;
        }
        DWInfo dwInfo = bp.nextDepositWithdrawal();
        dwInfo.setUid(Long.parseLong(uid));
        dwInfo.setCoinType(CoinType.findByValue(Integer.parseInt(coinType)));
        dwInfo.setAmount(Long.parseLong(amount));

        String dwType = values.getString(DW);
        if (dwType != null) {
            dwInfo.setDwtype(DOW.findByValue(Integer.parseInt(dwType)));
        }
        return true;
    }

    private void setTradePair(OrderInfo oi, String from, String to) {
        CoinType f = CoinType.findByValue(Integer.parseInt(from)),
            t = CoinType.findByValue(Integer.parseInt(to));
        if (oi.isSetTradePair()) {
            TradePair tp = oi.getTradePair();
            tp.setFrom(f);
            tp.setTo(t);
        } else {
            oi.setTradePair(new TradePair(f, t));
        }
    }

    private boolean createPlaceOrderCommand(MultiMap<String> values) {
        String uid = values.getString(UID),
            from = values.getString(FROM),
            to = values.getString(TO),
            quantity = values.getString(QUANTITY),
            bos = values.getString(BUY_OR_SELL),
            price = values.getString(PRICE);
        if (uid == null || from == null || to == null || quantity == null ||
            bos == null || price == null) {
            return false;
        }
        OrderInfo oi = bp.nextPlaceOrder();
        oi.setUid(Long.parseLong(uid));
        setTradePair(oi, from, to);
        oi.setQuantity(Long.parseLong(quantity));
        oi.setBos(BOS.findByValue(Integer.parseInt(bos)));
        oi.setPrice(Long.parseLong(price));

        String mol = values.getString(MARKET_OR_LIMIT),
            som = values.getString(SHORT_OR_MORE),
            strategy = values.getString(STRATEGY);
        if (mol != null) {
            oi.setMol(MOL.findByValue(Integer.parseInt(mol)));
        }
        if (som != null) {
            oi.setSom(SOM.findByValue(Integer.parseInt(som)));
        }
        if (strategy != null) {
            oi.setStrategy(Strategy.findByValue(Integer.parseInt(strategy)));
        }

        // TODO(c): add other params (etc actPrice) here
        return true;
    }

    private boolean createCancelOrderCommand(MultiMap<String> values) {
        String id = values.getString(ID);
        String uid = values.getString(UID);

        // TODO(c): make these two params as optional
        String from = values.getString(FROM);
        String to = values.getString(TO);
        if (uid == null || id == null || from == null || to == null) {
            return false;
        }
        OrderInfo oi = bp.nextCancelOrder();
        oi.setId(Long.parseLong(id));
        oi.setUid(Long.parseLong(uid));
        setTradePair(oi, from, to);
        return true;
    }

    @Override
    public void onMessage(String channel, String message) {
        logger.debug(message);

        MultiMap<String> values = new MultiMap<String>();
        UrlEncoded.decodeTo(message, values, "UTF-8", 1000);
        String commandTypeStr = values.getString(COMMAND_TYPE);
        if (commandTypeStr == null) {
            logger.warn("unknown the type of the command type");
            return;
        }
        BPCommandType t = BPCommandType.findByValue(Integer.parseInt(commandTypeStr));
        boolean isCommandCreated = false;
        switch (t) {
            case REGISTER_USER:
                isCommandCreated = createRegisterUserCommand(values);
                break;
            case DW:
                isCommandCreated = createDwCommand(values);
                break;
            case PLACE_ORDER:
                isCommandCreated = createPlaceOrderCommand(values);
                break;
            case CANCEL_ORDER:
                isCommandCreated = createCancelOrderCommand(values);
                break;
        }
        if (isCommandCreated)
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
