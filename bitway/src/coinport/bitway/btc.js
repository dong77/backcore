/**
 *Copyright 2014 Coinport Inc. All Rights Reserved.
 *Author: YangLi--ylautumn84@gmail.com
 *Filename: btc.js
 *Description: 
 */


var CryptoProxy = require('./crypto/crypto_proxy').CryptoProxy;
var RedisProxy  = require('./redis/redis_proxy').RedisProxy;
var DataTypes                       = require('../../../gen-nodejs/data_types')
    MessageTypes                    = require('../../../gen-nodejs/message_types')
    Bitcore                         = require('bitcore'),
    BitwayRequestType               = DataTypes.BitwayRequestType,
    Currency                        = DataTypes.Currency,
    BitwayMessage                   = MessageTypes.BitwayMessage;

var btcRpcConfig = {
    protocol: 'http',
    user: 'user',
    pass: 'pass',
    host: '127.0.0.1',
    port: '18332',
    };

var btcRedisProxy = new RedisProxy("BTC", "127.0.0.1", "6379");
btcRedisProxy.start();

var btcConfig = {
    cryptoRpc : new Bitcore.RpcClient(btcRpcConfig),
    minConfirm : 1,
    checkInterval : 5000,
};
var btcProxy = new CryptoProxy(Currency.BTC, btcConfig);

btcProxy.start();

btcRedisProxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
    btcProxy.generateUserAddress(request, function(message) {
        btcRedisProxy.publish(message); 
    });
});

btcRedisProxy.on(RedisProxy.EventType.TRANSFER, function(currency, request) {
    btcProxy.generateUserAddress(request, function(message) {
        btcRedisProxy.publish(message); 
    });
});

btcRedisProxy.on(RedisProxy.EventType.GET_MISSED_BLOCKS, function(currency, request) {
    btcProxy.generateUserAddress(request, function(error, message) {
        if (!error) {
            btcRedisProxy.publish(message); 
        }
    });
});

btcProxy.on(CryptoProxy.EventType.TX_ARRIVED, function(message) {
    btcRedisProxy.publish(message); 
});


btcProxy.on(CryptoProxy.EventType.BLOCK_ARRIVED, function(message) {
    btcRedisProxy.publish(message); 
});

btcProxy.on(CryptoProxy.EventType.HOT_ADDRESS_GENERATE, function(message) {
    btcRedisProxy.publish(message); 
});
