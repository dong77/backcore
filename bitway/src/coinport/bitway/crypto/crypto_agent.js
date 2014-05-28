/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var CryptoProxy = require('./crypto_proxy').CryptoProxy;
var RedisProxy  = require('../redis/redis_proxy').RedisProxy;

var CryptoAgent = module.exports.CryptoAgent = function(cryptoProxy, redisProxy) {
    var self = this;

    self.cryptoProxy = cryptoProxy;
    self.redisProxy = redisProxy;

    self.redisProxy.on(RedisProxy.EventType.SYNC_HOT_ADDRESSES, function(currency, request) {
        self.cryptoProxy.synchronousHotAddr(request, function(message) {
            self.redisProxy.publish(message);
        });
    });

    self.redisProxy.on(RedisProxy.EventType.GENERATE_ADDRESS, function(currency, request) {
        self.cryptoProxy.generateUserAddress(request, function(message) {
            self.redisProxy.publish(message);
        });
    });
    self.redisProxy.on(RedisProxy.EventType.TRANSFER, function(currency, request) {
        self.cryptoProxy.transfer(request, function(message) {
            self.redisProxy.publish(message);
        });
    });
    self.redisProxy.on(RedisProxy.EventType.GET_MISSED_BLOCKS, function(currency, request) {
        self.cryptoProxy.getMissedBlocks(request, function(error, message) {
            if (!error) {
                self.redisProxy.publish(message);
            }
        });
    });

    self.cryptoProxy.on(CryptoProxy.EventType.TX_ARRIVED, function(message) {
        self.redisProxy.publish(message);
    });


    self.cryptoProxy.on(CryptoProxy.EventType.BLOCK_ARRIVED, function(message) {
        self.redisProxy.publish(message);
    });

    self.cryptoProxy.on(CryptoProxy.EventType.HOT_ADDRESS_GENERATE, function(message) {
        self.redisProxy.publish(message);
    });
};

CryptoAgent.prototype.start = function() {
    this.redisProxy.start();
    this.cryptoProxy.start();
};
