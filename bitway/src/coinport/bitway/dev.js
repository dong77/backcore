/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var Bitcore                   = require('bitcore'),
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
    minConfirm: 1
});


cryptoProxy.generateUserAddress(new GenerateAddresses({num: 6}), function(response) {
    console.log('generateUserAddress: %j', response);
});


cryptoProxy.getBlockByIndex(1, function(error, response) {
    console.log('getBlockByIndex : %j', response);
});
