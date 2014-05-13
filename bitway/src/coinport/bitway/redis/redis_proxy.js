/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 * Usage: var proxy = new RedisProxy(BTC, "127.0.0.1", "6379");
 *        proxy.start();
 *        proxy.publish(xxx);
 */

var Events             = require('events'),
    Redis              = require('redis'),
    Util               = require("util"),
    Message            = require('../../../../gen-nodejs/message_types'),
    BitwayRequest      = Message.BitwayRequest,
    Data               = require('../../../../gen-nodejs/data_types'),
    BitwayRequestType  = Data.BitwayRequestType,
    Currency           = Data.Currency,
    Serializer         = require('../serializer/thrift_binary_serializer').ThriftBinarySerializer;

/**
 * The redis pub/sub clinet
 * @param {String} ip The ip of the redis server
 * @param {String} port The port of the redis server
 * @constructor
 * @extends {Events.EventEmitter}
 */
var RedisProxy = module.exports.RedisProxy = function(currency, ip, port) {
    Events.EventEmitter.call(this);

    RedisProxy.REQUEST_CHANNEL = 'creq_' + currency.toLowerCase();

    RedisProxy.RESPONSE_CHANNEL = 'cres_' + currency.toLowerCase();
    this.pollClient = Redis.createClient(port, ip, { return_buffers: true });
    this.pushClient = Redis.createClient(port, ip, { return_buffers: true });
    this.serializer = new Serializer();
};
Util.inherits(RedisProxy, Events.EventEmitter);

RedisProxy.EventType = {
    GENERATE_ADDRESS: 'generate_address',
    TRANSFER : 'transfer',
    GET_MISSED_BLOCKS: 'get_missed_blocks'
};


RedisProxy.prototype.start = function() {
    var listen = function(proxy) {
        proxy.pollClient.blpop(RedisProxy.REQUEST_CHANNEL, 0, function(error, result) {
            if (!error && result) {
                var buf = new Buffer(result[1]);
                var bwr = new BitwayRequest();
                proxy.serializer.fromBinary(bwr, buf);
                switch (bwr.type) {
                    case BitwayRequestType.GENERATE_ADDRESS:
                        console.log(bwr.currency);
                        console.log(bwr.generateAddresses.num);
                        proxy.emit(RedisProxy.EventType.GENERATE_ADDRESS, bwr.currency,
                            bwr.generateAddresses);
                        break;
                    case BitwayRequestType.TRANSFER:
                        proxy.emit(RedisProxy.EventType.TRANSFER, bwr.currency, bwr.transferCryptoCurrency);
                        break;
                    case BitwayRequestType.GET_MISSED_BLOCKS:
                        proxy.emit(RedisProxy.EventType.GET_MISSED_BLOCKS, bwr.currency,
                            bwr.getMissedCryptoCurrencyBlocksRequest);
                        break;
                }
            } else if (!error && !result) {
                console.log("timeout");
            } else {
                console.log(error);
            }
            listen(proxy);
        });
    };

    listen(this);
};

RedisProxy.prototype.publish = function(data) {
    var self = this;
    this.serializer.toBinary(data, function(bytes) {
        self.pushClient.rpush(RedisProxy.RESPONSE_CHANNEL, bytes);
    });
};
