/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var Assert                    = require('assert'),
    MockRpc                   = require('./mock_rpc').MockRpc,
    MockRedis                 = require('./mock_redis').MockRedis,
    CryptoProxy               = require('../../../../src/coinport/bitway/crypto/crypto_proxy').CryptoProxy,
    DataTypes                 = require('../../../../gen-nodejs/data_types'),
    MessageTypes              = require('../../../../gen-nodejs/message_types'),
    BitwayMessage             = MessageTypes.BitwayMessage,
    GenerateAddresses         = MessageTypes.GenerateAddresses,
    GenerateAddressesResult   = MessageTypes.GenerateAddressesResult,
    CryptoCurrencyAddressType = DataTypes.CryptoCurrencyAddressType,
    ErrorCode                 = DataTypes.ErrorCode,
    Currency                  = DataTypes.Currency,
    CryptoCurrencyTransaction = DataTypes.CryptoCurrencyTransaction;

describe('crypto proxy', function() {
    describe('generateUserAddress', function() {
        it('get 4 addresses', function(done) {
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'none'}),
                minConfirm: 1,
                redis: 'noredis'
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
                minConfirm: 1,
                redis: 'noredis'
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
                minConfirm: 1,
                redis: 'noredis'
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

    describe('getNewCCTXsSinceLatest_', function() {
        it('get new cctxs from latest height', function(done) {
            var redisClient = new MockRedis();
            redisClient.map['81fd055aeb9122673c3c98ca493871048eeb542c0876f3b5396f4b244501eca8'] = [1, 8];
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({blockCount: 244498}),
                minConfirm: 1,
                redis: redisClient
            });
            cryptoProxy.getNewCCTXsSinceLatest_(function(error, cctxs) {
                var expectedTxs = [new CryptoCurrencyTransaction ({
                    "sigId": "81fd055aeb9122673c3c98ca493871048eeb542c0876f3b5396f4b244501eca8",
                    "txid": "8debdd1691d1bff1e0b9f27cbf4958c9b7578e2bd0b50334a2bcc7060217e7a7",
                    "ids": [1, 8],
                    "inputs": [
                    {
                        "address": "n1YiTZ9SczJM5ZpRgBmRP2B5Gax7JHptAa",
                        "amount": 4.6498,
                        "internalAmount": null, "userId": null
                    }],
                    "outputs": [
                    {
                        "address": "mgen5m7yqkXckzqgug1cZFbtZz8cRZApqY",
                        "amount": 4.6178,
                        "internalAmount": null, "userId": null
                    },
                    {
                        "address": "mhfF1SYE8juppzWTFX2T7UBuSWT13yX5Jk",
                        "amount": 0.032,
                        "internalAmount": null, "userId":null
                    }],
                    "prevBlock": null,
                    "includedBlock": null,
                    "txType": null,
                    "status": 2,
                    "timestamp":null
                })];
                Assert.deepEqual(cctxs, expectedTxs);
                cryptoProxy.getNewCCTXsSinceLatest_(function(error, cctxs) {
                    Assert.deepEqual(cctxs, []);
                    done();
                });
            });
        });
    });

    describe('getNextCCBlock_', function() {
        it('get new block for newest height', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'partial', blockCount: 244498}),
                minConfirm: 1,
                redis: redisClient
            });
            cryptoProxy.getNextCCBlock_(function(error, block) {
                Assert.deepEqual(redisClient.map, {'1000_processed_sigids': undefined, '1000_last_index': 244498});
                Assert.equal(block.index.id, '000000003471884e402aa2383121c4cc9e4f769c6d16e1ce920a7c35d852f872');
                Assert.equal(block.txs.length, 18);
                Assert.deepEqual(block.txs[1].inputs, [{"address":"n2JjGvghqD9vPF1HGnxHiKABmCZUEskwEU","amount":2.823,"internalAmount":null,"userId":null}]);
                Assert.deepEqual(block.txs[1].outputs, [
                    {"address":"mkSmF1qmmpdaaSLt2qayVitSedX7stXbSQ","amount":0.3,"internalAmount":null,"userId":null},
                    {"address":"mrCC7TwxfTTMxC796474wTmbXN1n5JWLu3","amount":2.523,"internalAmount":null,"userId":null}]);
                done();
            });
        });
    });
});
