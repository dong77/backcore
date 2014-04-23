/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 * Usage: var proxy = new RedisProxy("127.0.0.1", "6379");
 *        proxy.start();
 *        proxy.publish(xxx);
 */

var Events         = require('events'),
    Redis          = require('redis'),
    Util           = require("util"),
    Message        = require('../../../../gen-nodejs/message_types'),
    BitwayRequest  = Message.BitwayRequest,
    Data           = require('../../../../gen-nodejs/data_types'),
    BitwayType     = Data.BitwayType,
    Currency       = Data.Currency,
    Serializer     = require('../serializer/thrift_binary_serializer').ThriftBinarySerializer;

/**
 * The redis pub/sub clinet
 * @param {String} ip The ip of the redis server
 * @param {String} port The port of the redis server
 * @constructor
 * @extends {Events.EventEmitter}
 */
var RedisProxy = module.exports.RedisProxy = function(ip, port) {
    Events.EventEmitter.call(this);

    this.pollClient = Redis.createClient(port, ip, { return_buffers: true });
    this.pushClient = Redis.createClient(port, ip, { return_buffers: true });
    this.serializer = new Serializer();
};
Util.inherits(RedisProxy, Events.EventEmitter);

RedisProxy.EventType = {
    GENERATE_WALLET : 'generate_wallet',
    TRANSFER : 'transfer',
    QUERY_WALLET : 'query_wallet'
};

RedisProxy.REQUEST_CHANNEL = 'creq';

RedisProxy.RESPONSE_CHANNEL = 'cres';

RedisProxy.prototype.start = function() {
    var listen = function(proxy) {
        proxy.pollClient.blpop(RedisProxy.REQUEST_CHANNEL, 0, function(error, result) {
            if (!error && result) {
                var buf = new Buffer(result[1]);
                var bwr = new BitwayRequest();
                proxy.serializer.fromBinary(bwr, buf);
                switch (bwr.type) {
                    case BitwayType.GENERATE_WALLET:
                        proxy.emit(RedisProxy.EventType.GENERATE_WALLET, bwr.requestId, bwr.currency,
                            bwr.generateWalletRequest);
                        break;
                    case BitwayType.TRANSFER:
                        proxy.emit(RedisProxy.EventType.TRANSFER, bwr.requestId, bwr.currency, bwr.transferRequest);
                        break;
                    case BitwayType.QUERY_WALLET:
                        proxy.emit(RedisProxy.EventType.QUERY_WALLET, bwr.requestId, bwr.currency,
                            bwr.queryWalletRequest);
                        break;
                }
            } else if (!result) {
                console.log("timeout")
            } else {
                console.log(error)
            }
            listen(proxy);
        });
    };

    listen(this);
};

RedisProxy.prototype.publish = function(data) {
    var self = this;
    this.serializer.toBinary(data, function(bytes) {
        console.log(bytes);
        self.pushClient.rpush(RedisProxy.RESPONSE_CHANNEL, bytes);
    });
};
