/**
 * Copyright 2014 Coinport Inc. All Rights Reserved.
 * Author: c@coinport.com (Chao Ma)
 */

'use strict'

var Assert                    = require('assert'),
    MockRpc                   = require('./mock_rpc').MockRpc,
    MockDogRpc                = require('./mock_dog_rpc').MockDogRpc,
    MockDogData               = require('./mock_dog_data').MockDogData,
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
    TransferType              = DataTypes.TransferType,
    CryptoCurrencyTransaction = DataTypes.CryptoCurrencyTransaction;

describe('crypto proxy', function() {
    describe('generateUserAddress', function() {
        it('get 4 addresses', function(done) {
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'none'}),
                minConfirm: 1,
                redis: new MockRedis()
            });
            cryptoProxy.generateUserAddress(new GenerateAddresses({num: 4}), function(response) {
                var expectRes = new BitwayMessage({currency: Currency.BTC, generateAddressResponse:
                    new GenerateAddressesResult({error: ErrorCode.OK, 
                        addresses: [{"address":"addr","privateKey":"priv","nxtRsAddress":null,"accountName":null,"signMessage":null,"message":null,"nxtPublicKey":null},
                        {"address":"addr","privateKey":"priv","nxtRsAddress":null,"accountName":null,"signMessage":null,"message":null,"nxtPublicKey":null},
                        {"address":"addr","privateKey":"priv","nxtRsAddress":null,"accountName":null,"signMessage":null,"message":null,"nxtPublicKey":null},
                        {"address":"addr","privateKey":"priv","nxtRsAddress":null,"accountName":null,"signMessage":null,"message":null,"nxtPublicKey":null}],
                        addressType: CryptoCurrencyAddressType.UNUSED})})
                Assert.deepEqual(response, expectRes);
                done();
            });
        });
        it('rpc error occur while generating user addresses', function(done) {
            var cryptoProxy = new CryptoProxy(Currency.BTC, {
                cryptoRpc: new MockRpc({fail: 'all'}),
                minConfirm: 1,
                redis: new MockRedis()
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
                redis: new MockRedis()
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
                        "internalAmount": null, "userId": null,
                        "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null
                    }],
                    "outputs": [
                    {
                        "address": "mgen5m7yqkXckzqgug1cZFbtZz8cRZApqY",
                        "amount": 4.6178,
                        "internalAmount": null, "userId": null,
                        "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null
                    },
                    {
                        "address": "mhfF1SYE8juppzWTFX2T7UBuSWT13yX5Jk",
                        "amount": 0.032,
                        "internalAmount": null, "userId":null,
                        "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null
                    }],
                    "prevBlock": null,
                    "includedBlock": null,
                    "txType": null,
                    "status": 2,
                    "timestamp":null
                })];
                Assert.deepEqual(cctxs, expectedTxs);
                cryptoProxy.getNewCCTXsSinceLatest_(function(error, cctxs) {
                    Assert.deepEqual(redisClient.map, {"81fd055aeb9122673c3c98ca493871048eeb542c0876f3b5396f4b244501eca8":[1,8],"1000_processed_sigids_244498":["81fd055aeb9122673c3c98ca493871048eeb542c0876f3b5396f4b244501eca8"]});
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
                Assert.deepEqual(redisClient.map, {'1000_processed_sigids_244497': undefined, '1000_last_index': 244498});
                Assert.equal(block.index.id, '000000003471884e402aa2383121c4cc9e4f769c6d16e1ce920a7c35d852f872');
                Assert.equal(block.txs.length, 18);
                Assert.deepEqual(block.txs[1].inputs, [{"address":"n2JjGvghqD9vPF1HGnxHiKABmCZUEskwEU","amount":2.823,
                    "internalAmount":null,"userId":null,"nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null}]);
                Assert.deepEqual(block.txs[1].outputs, [
                    {"address":"mkSmF1qmmpdaaSLt2qayVitSedX7stXbSQ","amount":0.3,"internalAmount":null,"userId":null,
                        "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null},
                    {"address":"mrCC7TwxfTTMxC796474wTmbXN1n5JWLu3","amount":2.523,"internalAmount":null,"userId":null,
                        "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null}]);
                done();
            });
        });
    });

    describe('constructRawTransaction_', function() {
        it('withdrawal/hot to cold success', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var transferInfoA = {from: "", to: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", amount: 5, id: '10001'};
            var transferInfoB = {from: "", to: "nkUjTEVGs9x52FQPAkiEz5XoQHj5A9Tg4F", amount: 10, id: '10002'};
            var transferInfos = [];
            transferInfos.push(transferInfoA);
            transferInfos.push(transferInfoB);
            var request = {currency: Currency.DOGE, transferInfos: transferInfos, type: TransferType.WITHDRAWAL};
            cryptoProxy.constructRawTransaction_(request, function(error, rawData) {
                Assert.deepEqual(rawData.transactions, [
                    {"txid":"fca649288b456891a17f3997f6a772a4890cb15a025cfffe13e4896b5a53da2a","vout":0}]);
                done();
            });
        });

        it('withdrawal/hot to cold fail', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var transferInfoA = {from: "", to: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", amount: 5, id: '10001'};
            var transferInfoB = {from: "", to: "nkUjTEVGs9x52FQPAkiEz5XoQHj5A9Tg4F", amount: 100000, id: '10002'};
            var transferInfos = [];
            transferInfos.push(transferInfoA);
            transferInfos.push(transferInfoB);
            var request = {currency: Currency.DOGE, transferInfos: transferInfos, type: TransferType.WITHDRAWAL};
            cryptoProxy.constructRawTransaction_(request, function(error, rawData) {
                Assert.equal(error.code, "Lack of balance!");
                Assert.equal(error.message, "Lack of balance!");
                done();
            });
        });


        it('user to hot(single address)', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var transferInfoA = {from: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", to: "", amount: 15, id: '10001'};
            var transferInfos = [];
            transferInfos.push(transferInfoA);
            var request = {currency: Currency.DOGE, transferInfos: transferInfos, type: TransferType.USER_TO_HOT};
            cryptoProxy.constructRawTransaction_(request, function(error, rawData) {
                Assert.equal(error, null);
                Assert.deepEqual(rawData.transactions, [{"txid":"f72cf190df6c13b704c4830fc043a4b7b0ef56ad3d4bcf0324928365dd82cfdc","vout":0},
                    {"txid":"854dbdbefde63e0435fee1519e3893860d6687061ebb79624a0542968a29a92b","vout":1}]);
                var hotAddresses = MockDogData.addressesByAccount["hot"];
                var flag = false;
                for (var i = 0; i < hotAddresses.length; i++) {
                    if (rawData.addresses[hotAddresses[i]] == 14.9999) {
                        flag = true;
                        break;
                    }
                }
                Assert.equal(flag, true);
                done();
            });
        });

        it('user to hot(duplicate addresses)', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var transferInfoA = {from: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", to: "", amount: 4.99995, id: '10001'};
            var transferInfoB = {from: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", to: "", amount: 10, id: '10002'};
            var transferInfos = [];
            transferInfos.push(transferInfoA);
            transferInfos.push(transferInfoB);
            var request = {currency: Currency.DOGE, transferInfos: transferInfos, type: TransferType.USER_TO_HOT};
            cryptoProxy.constructRawTransaction_(request, function(error, rawData) {
                Assert.equal(error, null);
                Assert.deepEqual(rawData.transactions, [{"txid":"f72cf190df6c13b704c4830fc043a4b7b0ef56ad3d4bcf0324928365dd82cfdc","vout":0},
                    {"txid":"854dbdbefde63e0435fee1519e3893860d6687061ebb79624a0542968a29a92b","vout":1}]);
                done();
            });
        });


        it('user to hot(multi addresses)', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var transferInfoA = {from: "njUFyRxUrD5EgssHhX14Nr31k9piigMwQg", to: "", amount: 10000, id: '10001'};
            var transferInfoB = {from: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", to: "", amount: 5, id: '10002'};
            var transferInfoC = {from: "nmoEYptTPTRfzn9U8x58q97vioduyS98dE", to: "", amount: 5, id: '10003'};
            var transferInfos = [];
            transferInfos.push(transferInfoA);
            transferInfos.push(transferInfoB);
            transferInfos.push(transferInfoC);
            var request = {currency: Currency.DOGE, transferInfos: transferInfos, type: TransferType.USER_TO_HOT};
            cryptoProxy.constructRawTransaction_(request, function(error, rawData) {
                Assert.equal(error, null);
                Assert.deepEqual(rawData.transactions, [{"txid":"57048399a409eb0778f478e9702b0adcbf4e05726da5659f0dc41606ec9616fb","vout":1},
                    {"txid":"f72cf190df6c13b704c4830fc043a4b7b0ef56ad3d4bcf0324928365dd82cfdc","vout":0},
                    {"txid":"854dbdbefde63e0435fee1519e3893860d6687061ebb79624a0542968a29a92b","vout":1}]);
                Assert.equal(rawData.addresses['njUFyRxUrD5EgssHhX14Nr31k9piigMwQg'], 190000);
                Assert.equal(rawData.addresses['nmoEYptTPTRfzn9U8x58q97vioduyS98dE'], 5);
                var hotAddresses = MockDogData.addressesByAccount["hot"];
                var flag = false;
                for (var i = 0; i < hotAddresses.length; i++) {
                    if (rawData.addresses[hotAddresses[i]] == 10009.9999) {
                        flag = true;
                        break;
                    }
                }
                Assert.equal(flag, true);
                done();
            });
        });

    });

    describe('getMissedBlocks', function() {
        it('get missed blocks', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var startA = new BlockIndex({id: '778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b', height: 93716});
            var startB = new BlockIndex({id: '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6', height:93717});
            var startIndexs = [];
            startIndexs.push(startA);
            startIndexs.push(startB);
            var endIndex = new BlockIndex({id: 'd661700668f723ea29be1461d6dedd4cd27c52480896ab368fad1100d15f35a5', height:93721});
            var request = new GetMissedCryptoCurrencyBlocks({startIndexs: startIndexs, endIndex: endIndex});
            cryptoProxy.getMissedBlocks(request, function(error, message) {
                Assert.equal(message.currency, Currency.DOGE);
                Assert.equal(message.blockMsg.reorgIndex.id, "05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6");
                Assert.equal(message.blockMsg.reorgIndex.height, 93717);
                var block = message.blockMsg.block;
                Assert.deepEqual(redisClient.map, {'1100_last_index': 93718});
                Assert.equal(block.index.id, '79123d5fae236fa9f49b7a6f4f3c48367ca96fd59b1de5450827099b9260fa5d');
                Assert.equal(block.index.height, 93718);
                Assert.equal(block.prevIndex.id, '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6');
                Assert.equal(block.prevIndex.height, 93717);
                Assert.equal(block.txs.length, 1);
                Assert.equal(block.txs[0].sigId, "d1d2883deeea0f4bbda6d5e0294026608141d59482f446947cea937c8d14625b");
                Assert.equal(block.txs[0].txid, "367e72d39abc002d930745b64a4b36d5af783094b93da5c2e8a2cc82ece9ad3a");
                Assert.deepEqual(block.txs[0].inputs, [{"address":"coinbase","amount":0,
                    "internalAmount":null,"userId":null,"nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null}]);
                Assert.deepEqual(block.txs[0].outputs, [
                    {"address":"nrrPrREiPuZua2XmL7YMj4BDSCuZKhemQo","amount":535256,"internalAmount":null,"userId":null,
                        "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null}]);
                done();
            });
        });
    });


    describe('getMissedBlocks', function() {
        it('get the missed bloks while the block chain has forked', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var startA = new BlockIndex({id: '778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b', height: 93716});
            var startB = new BlockIndex({id: 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', height:93717});
            var startIndexs = [];
            startIndexs.push(startA);
            startIndexs.push(startB);
            var endIndex = new BlockIndex({id: 'd661700668f723ea29be1461d6dedd4cd27c52480896ab368fad1100d15f35a5', height:93721});
            var request = new GetMissedCryptoCurrencyBlocks({startIndexs: startIndexs, endIndex: endIndex});
            cryptoProxy.getMissedBlocks(request, function(error, message) {
                Assert.equal(message.currency, Currency.DOGE);
                Assert.equal(message.blockMsg.reorgIndex.id, '778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b');
                Assert.equal(message.blockMsg.reorgIndex.height, 93716);
                var block = message.blockMsg.block;
                Assert.deepEqual(redisClient.map, {'1100_last_index': 93717});
                Assert.equal(block.index.id, '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6');
                Assert.equal(block.index.height, 93717);
                Assert.equal(block.prevIndex.id, '778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b');
                Assert.equal(block.prevIndex.height, 93716);
                Assert.equal(block.txs.length, 1);
                Assert.deepEqual(block.txs[0].sigId, "8a1022f784393927f36d177a63a8d44d8e26dac211baafcd68ab8251615651ad");
                Assert.deepEqual(block.txs[0].txid, "acb2aa6da8fb98d9ee650de1aad8ae181b8b387fd3af2d7c89d0003efa1bff87");
                Assert.deepEqual(block.txs[0].inputs, [{"address":"coinbase","amount":0,
                    "internalAmount":null,"userId":null,"nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null}]);
                Assert.deepEqual(block.txs[0].outputs, [
                    {"address":"ne5quHyCBQc9kL4zHxR38gVSFu3tb9AEbN","amount":603718,"internalAmount":null,"userId":null,
                    "nxtRsAddress":null,"accountName":null,"memo":null,"nxtPublicKey":null}]);
                done();
            });
        });
    });


    describe('getMissedBlocks', function() {
        it('getMissedBlocks while seriously forked', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var startA = new BlockIndex({id: 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', height: 93716});
            var startB = new BlockIndex({id: 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx', height:93717});
            var startIndexs = [];
            startIndexs.push(startA);
            startIndexs.push(startB);
            var endIndex = new BlockIndex({id: 'd661700668f723ea29be1461d6dedd4cd27c52480896ab368fad1100d15f35a5', height:93721});
            var request = new GetMissedCryptoCurrencyBlocks({startIndexs: startIndexs, endIndex: endIndex});
            cryptoProxy.getMissedBlocks(request, function(error, message) {
                Assert.equal(message.currency, Currency.DOGE);
                Assert.equal(message.blockMsg.reorgIndex.id, null)
                Assert.equal(message.blockMsg.reorgIndex.height, null);
                var block = message.blockMsg.block;
                Assert.deepEqual(redisClient.map, {});
                Assert.equal(block, null);
                done();
            });
        });
    });

    describe('getReorgPosition_', function() {
        it('get the reorg position', function(done) {
            var redisClient = new MockRedis();
            var cryptoProxy = new CryptoProxy(Currency.DOGE, {
                cryptoRpc: new MockDogRpc({blockCount: 93721}),
                minConfirm: 1,
                redis: redisClient
            });
            var startA = new BlockIndex({id: '778835f7edb20df20174afe9316ae2339c9526c9bd2dca9b8d81406f774d6e0b', height: 93716});
            var startB = new BlockIndex({id: '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6', height:93717});
            var startIndexs = [];
            startIndexs.push(startA);
            startIndexs.push(startB);
            var endIndex = new BlockIndex({id: 'd661700668f723ea29be1461d6dedd4cd27c52480896ab368fad1100d15f35a5', height:93721});
            var request = new GetMissedCryptoCurrencyBlocks({startIndexs: startIndexs, endIndex: endIndex});
            cryptoProxy.getReorgPosition_(request, function(error, block) {
                Assert.equal(block.id, '05a23efad2b301d280d5f5379ca5b31d586c685eab2b30ca543f213ce067afc6');
                Assert.equal(block.height, 93717);
                done();
            });
        });
    });
});
