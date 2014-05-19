/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var Async                     = require('async'),
    Bitcore                   = require('bitcore'),
    Events                    = require('events'),
    Util                      = require("util"),
    DataTypes                 = require('../../../../gen-nodejs/data_types'),
    MessageTypes              = require('../../../../gen-nodejs/message_types'),
    BitwayMessage             = MessageTypes.BitwayMessage,
    BitwayResponseType        = DataTypes.BitwayResponseType,
    ErrorCode                 = DataTypes.ErrorCode,
    GenerateAddressesResult   = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyAddressType = DataTypes.CryptoCurrencyAddressType;

/**
 * Handle the crypto currency network event
 * @param {Currency} currency The handled currency type
 * @param {Map{...}} config The config for CryptoProxy
 *     {
 *       cryptoRpc: xxx,
 *       checkInterval: 5000,
 *       minConfirm: 1
 *     }
 * @constructor
 * @extends {Events.EventEmitter}
 */
var CryptoProxy = module.exports.CryptoProxy = function(currency, config) {
    Events.EventEmitter.call(this);

    this.currency = currency;
    this.minConfirm = config.minConfirm;
    this.rpc = config.cryptoRpc;
};
Util.inherits(CryptoProxy, Events.EventEmitter);

CryptoProxy.ACCOUNT = 'customers';
CryptoProxy.HOT_ACCOUNT = "hot";
CryptoProxy.TIP = 0.0001;
CryptoProxy.MIN_GENERATE_ADDR_NUM = 1;
CryptoProxy.MAX_GENERATE_ADDR_NUM = 1000;
CryptoProxy.MIN_CONFIRM_NUM = 0;
CryptoProxy.MAX_CONFIRM_NUM = 9999999;

CryptoProxy.prototype.generateOneAddress = function(unusedIndex, callback) {
    this.rpc.getNewAddress(CryptoProxy.ACCOUNT, function(error, address) {
        if (error) {
            callback(error);
        } else {
            callback(null, address.result);
        }
    });
};

CryptoProxy.prototype.generateUserAddress = function(request, callback) {
    var self = this;
    if (request.num < CryptoProxy.MIN_GENERATE_ADDR_NUM || request.num > CryptoProxy.MAX_GENERATE_ADDR_NUM) {
        callback(self.makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, self.currency,
            new GenerateAddressesResult({error: ErrorCode.INVALID_REQUEST_ADDRESS_NUM})));
    } else {
        Async.times(request.num, self.generateOneAddress.bind(self), function(error, results) {
            if (error || results.length != request.num) {
                callback(self.makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, self.currency,
                    new GenerateAddressesResult({error: ErrorCode.RPC_ERROR,
                        addressType: CryptoCurrencyAddressType.UNUSED})));
            } else {
                callback(self.makeNormalResponse(BitwayResponseType.GENERATE_ADDRESS, self.currency,
                    new GenerateAddressesResult({error: ErrorCode.OK,
                        addresses: results, addressType: CryptoCurrencyAddressType.UNUSED})));
            }
        });
    }
};

CryptoProxy.prototype.makeNormalResponse = function(type, currency, response) {
    switch (type) {
        case BitwayResponseType.GENERATE_ADDRESS:
            return new BitwayMessage({currency: currency, generateAddressResponse: response});
        /*
        case BitwayResponseType.TRANSFER:
        case BitwayResponseType.TRANSACTION:
            console.log("TRANSACTION REPORT: " + currency);
            displayTxContent(response);
            redisProxy.publish(new BitwayMessage({currency: currency, tx: response}));
            break;
        case BitwayResponseType.GET_MISSED_BLOCKS:
        case BitwayResponseType.AUTO_REPORT_BLOCKS:
            console.log("BLOCK REPORT: " + currency);
            console.log("response.blocks.length:" + response.blocks.length);
            displayBlocksContent(response.blocks);
            redisProxy.publish(new BitwayMessage({currency: currency, blocksMsg: response}));
            break;
        */
        default:
            console.log("Inavalid Type!");
            return null
    }
};
