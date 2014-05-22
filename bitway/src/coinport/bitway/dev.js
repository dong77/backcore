/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var Bitcore                   = require('bitcore'),
    Redis                     = require('redis'),
    CryptoProxy               = require('./crypto/crypto_proxy').CryptoProxy,
    DataTypes                 = require('../../../gen-nodejs/data_types'),
    MessageTypes              = require('../../../gen-nodejs/message_types'),
    BitwayMessage             = MessageTypes.BitwayMessage,
    GenerateAddresses         = MessageTypes.GenerateAddresses,
    GenerateAddressesResult   = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyAddressType = DataTypes.CryptoCurrencyAddressType,
    ErrorCode                 = DataTypes.ErrorCode,
    Currency                  = DataTypes.Currency;

var cryptoProxy = new CryptoProxy(Currency.BTC, {
    cryptoRpc: new Bitcore.RpcClient({
        protocol: 'http',
        user: 'user',
        pass: 'pass',
        host: '127.0.0.1',
        port: '18332',
    }),
    redis: Redis.createClient('6379', '127.0.0.1', { return_buffers: true }),
    minConfirm: 1,
    checkInterval: 1000
});


/*
cryptoProxy.generateUserAddress(new GenerateAddresses({num: 6}), function(response) {
    console.log('generateUserAddress: %j', response);
});


cryptoProxy.getBlockByIndex_(1, function(error, response) {
    console.log('getBlockByIndex_ : %j', response);
});
*/

/*
cryptoProxy.getCCBlockByIndex_(244378, function(error, response) {
    console.log('getCCBlockByIndex_ : %j', response);
});

cryptoProxy.getCCBlockByIndex_(123, function(error, response) {
    console.log('getCCBlockByIndex_ : %j', response);
});
*/
cryptoProxy.on(CryptoProxy.EventType.TX_ARRIVED, function(cctx) {
    console.log('%j', cctx);
});

cryptoProxy.on(CryptoProxy.EventType.BLOCK_ARRIVED, function(block) {
    console.log('%j', block);
});
cryptoProxy.start();
