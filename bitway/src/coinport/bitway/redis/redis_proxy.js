/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 * Usage: var proxy = new RedisProxy("btc", "127.0.0.1", "6379");
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
    Logger             = require('../logger'),
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

    this.currency = currency;

    this.REQUEST_CHANNEL = 'creq_' + currency.toString();
    this.RESPONSE_CHANNEL = 'cres_' + currency.toString();

    this.pollClient = Redis.createClient(port, ip, { return_buffers: true });
    this.pushClient = Redis.createClient(port, ip, { return_buffers: true });
    this.pollClient.on('ready'       , this.logFunction('ready'));
    this.pollClient.on('reconnecting', this.logFunction('reconnecting'));
    this.pollClient.on('error'       , this.logFunction('error'));
    this.pollClient.on('end'         , this.logFunction('end'));

    this.pushClient.on('connect'     , this.logFunction('connect'));
    this.pushClient.on('ready'       , this.logFunction('ready'));
    this.pushClient.on('reconnecting', this.logFunction('reconnecting'));
    this.pushClient.on('error'       , this.logFunction('error'));
    this.pushClient.on('end'         , this.logFunction('end'));
    this.serializer = new Serializer();
    this.log = Logger.logger(this.currency.toString());
};
Util.inherits(RedisProxy, Events.EventEmitter);

RedisProxy.EventType = {
    GENERATE_ADDRESS : 'generate_address',
    TRANSFER : 'transfer',
    GET_MISSED_BLOCKS : 'get_missed_blocks',
    SYNC_HOT_ADDRESSES : 'sync_hot_addresses',
    MULTI_TRANSFER : 'multi_transfer',
    SYNC_PRIVATE_KEYS: 'sync_prviate_keys'
};

RedisProxy.prototype.logFunction = function log(type) {
    var self = this;
    return function() {
        self.log.info(type);
    };
};

RedisProxy.prototype.listen = function() {
    var self = this;
    self.pollClient.blpop(self.REQUEST_CHANNEL, 0, function(error, result) {
        if (!error && result) {
            var buf = new Buffer(result[1]);
            var bwr = new BitwayRequest();
            self.serializer.fromBinary(bwr, buf);
            switch (bwr.type) {
                case BitwayRequestType.SYNC_HOT_ADDRESSES:
                    self.log.info(bwr.currency);
                    self.log.info(bwr.syncHotAddresses);
                    self.emit(RedisProxy.EventType.SYNC_HOT_ADDRESSES, bwr.currency,
                        bwr.syncHotAddresses);
                    break;
                case BitwayRequestType.SYNC_PRIVATE_KEYS:
                    self.emit(RedisProxy.EventType.SYNC_PRIVATE_KEYS, bwr.currency,
                        bwr.syncPrivateKeys);
                    break;
                case BitwayRequestType.GENERATE_ADDRESS:
                    self.log.info(bwr.currency);
                    self.log.info(bwr.generateAddresses.num);
                    self.emit(RedisProxy.EventType.GENERATE_ADDRESS, bwr.currency,
                        bwr.generateAddresses);
                    break;
                case BitwayRequestType.TRANSFER:
                    self.emit(RedisProxy.EventType.TRANSFER, bwr.currency, bwr.transferCryptoCurrency);
                    break;
                case BitwayRequestType.MULTI_TRANSFER:
                    self.emit(RedisProxy.EventType.MULTI_TRANSFER, bwr.currency, bwr.multiTransferCryptoCurrency);
                    break;
                case BitwayRequestType.GET_MISSED_BLOCKS:
                    self.emit(RedisProxy.EventType.GET_MISSED_BLOCKS, bwr.currency,
                            bwr.getMissedCryptoCurrencyBlocksRequest);
                    break;
            }
            self.listen();
        } else if (!error && !result) {
            self.log.info("timeout");
            self.listen();
        } else {
            self.log.info(error);
        }
    });
};

RedisProxy.prototype.start = function() {
    var self = this;
    this.pollClient.on('connect', function() {
        self.log.info('connect');
        self.listen();
    });
};

RedisProxy.prototype.publish = function(data) {
    var self = this;
    this.serializer.toBinary(data, function(bytes) {
        self.pushClient.rpush(self.RESPONSE_CHANNEL, bytes);
    });
};
