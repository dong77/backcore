/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var RedisProxy              = require('./redis/redis_proxy').RedisProxy,
    BitwayType              = require('../../../gen-nodejs/data_types').BitwayType,
    GenerateAddressResponse = require('../../../gen-nodejs/data_types').GenerateAddressResponse,
    Currency                = require('../../../gen-nodejs/data_types').Currency,
    ErrorCode               = require('../../../gen-nodejs/data_types').ErrorCode,
    BitwayResponse          = require('../../../gen-nodejs/message_types').BitwayResponse,
    Bitcore                 = require('bitcore'),
    Peer                    = Bitcore.Peer,
    Networks                = Bitcore.networks,
    PeerManager             = require('soop').load('bitcore/PeerManager', {
                                  network: Networks.testnet
                              });

var proxy = new RedisProxy("127.0.0.1", "6379");

proxy.on(RedisProxy.EventType.GENERATE_WALLET, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.GENERATE_WALLET);
    console.log(requestId);
    console.log(currency);
    console.log(request);
});

proxy.on(RedisProxy.EventType.TRANSFER, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.TRANSFER);
    console.log(requestId);
    console.log(requestId === 123);
    console.log(currency);
    console.log(request);
});

proxy.on(RedisProxy.EventType.QUERY_WALLET, function(requestId, currency, request) {
    console.log(RedisProxy.EventType.QUERY_WALLET);
    console.log(requestId);
    console.log(currency);
    console.log(request);
});

proxy.start();

// proxy.publish(new BitwayResponse({type: BitwayType.GENERATE_WALLET, requestId: 1425, currency: Currency.BTC,
    // generateAddressResponse: new GenerateAddressResponse({error: ErrorCode.ROBOT_DNA_EXIST})}))

var handleBlock = function(info) {
    console.log('** Block Received **');
    console.log(info.message);
    proxy.publish(new BitwayResponse({type: BitwayType.GENERATE_WALLET, requestId: 1425, currency: Currency.BTC,
        generateAddressResponse: new GenerateAddressResponse({error: ErrorCode.ROBOT_DNA_EXIST})}));
};

var handleTx = function(info) {
    var tx = info.message.tx.getStandardizedObject();

    console.log('** TX Received **');
    console.log(tx);
    proxy.publish(new BitwayResponse({type: BitwayType.GENERATE_WALLET, requestId: 1425, currency: Currency.BTC,
        generateAddressResponse: new GenerateAddressResponse({error: ErrorCode.ROBOT_DNA_EXIST})}));
};

var handleInv = function(info) {
    console.log('** Inv **');
    console.log(info.message);

    var invs = info.message.invs;
    info.conn.sendGetData(invs);
    proxy.publish(new BitwayResponse({type: BitwayType.GENERATE_WALLET, requestId: 1425, currency: Currency.BTC,
        generateAddressResponse: new GenerateAddressResponse({error: ErrorCode.ROBOT_DNA_EXIST})}));
};

var peerman = new PeerManager();

peerman.addPeer(new Peer('127.0.0.1', 18333));

peerman.on('connection', function(conn) {
    conn.on('inv', handleInv);
    conn.on('block', handleBlock);
    conn.on('tx', handleTx);
});

peerman.start();

var logo = "" +
" _    _ _                     \n" +
"| |__(_) |___ __ ____ _ _  _  \n" +
"| '_ \\ |  _\\ V  V / _` | || | \n" +
"|_.__/_|\\__|\\_/\\_/\\__,_|\\_, | \n" +
"                        |__/  \n";
console.log(logo);
