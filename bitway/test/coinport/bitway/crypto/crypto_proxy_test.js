/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

var Assert                    = require('assert'),
    MockRpc                   = require('./mock_rpc').MockRpc,
    CryptoProxy               = require('../../../../src/coinport/bitway/crypto/crypto_proxy').CryptoProxy,
    DataTypes                 = require('../../../../gen-nodejs/data_types'),
    MessageTypes              = require('../../../../gen-nodejs/message_types'),
    BitwayMessage             = MessageTypes.BitwayMessage,
    GenerateAddresses         = MessageTypes.GenerateAddresses,
    GenerateAddressesResult   = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyAddressType = DataTypes.CryptoCurrencyAddressType,
    ErrorCode                 = DataTypes.ErrorCode,
    Currency                  = DataTypes.Currency;

describe('crypto proxy', function() {
    describe('generateUserAddress', function() {
        it('get 4 addresses', function(done) {
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'none'}),
                minClients: 1
            });
            cryptoProxy.generateUserAddress(new GenerateAddresses({num: 4}), function(response) {
                var expectRes = new BitwayMessage({currency: Currency.BTC, generateAddressResponse:
                    new GenerateAddressesResult({error: ErrorCode.OK, addresses: ['addr', 'addr', 'addr', 'addr'],
                        addressType: CryptoCurrencyAddressType.UNUSED})})
                Assert.deepEqual(response, expectRes);
                done();
            });
        });
        it('rpc error occur while generating user addresses', function(done) {
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'all'}),
                minClients: 1
            });
            cryptoProxy.generateUserAddress(new GenerateAddresses({num: 4}), function(response) {
                var expectRes = new BitwayMessage({currency: Currency.BTC, generateAddressResponse:
                    new GenerateAddressesResult({error: ErrorCode.RPC_ERROR, addresses: null,
                        addressType: CryptoCurrencyAddressType.UNUSED})});
                Assert.deepEqual(response, expectRes);
                done();
            });
        });

        it('partial fail while generating user addresses', function(done) {
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'partial'}),
                minClients: 1
            });
            cryptoProxy.generateUserAddress(new GenerateAddresses({num: 4}), function(response) {
                var expectRes = new BitwayMessage({currency: Currency.BTC, generateAddressResponse:
                    new GenerateAddressesResult({error: ErrorCode.RPC_ERROR, addresses: null,
                        addressType: CryptoCurrencyAddressType.UNUSED})});
                Assert.deepEqual(response, expectRes);
                done();
            });
        });
    });
});
